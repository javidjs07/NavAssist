package com.example.navassist

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Foreground service that draws the draggable floating orb and, when tapped, a glass-style menu
 * with navigation / utility buttons. Orb gestures (tap, double tap, long press) are configurable.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var orb: FrameLayout? = null
    private var orbLp: WindowManager.LayoutParams? = null
    private var scrim: FrameLayout? = null
    private var torchOn = false
    private var hidden = false
    private var recHidden = false
    private var tempHidden = false
    private var peeked = false
    private var dockX = 0
    private var menuPage = 0
    private var pendingTap = false
    private var longFired = false
    private var sliderTouch = false

    private val th get() = Themes.theme(this)

    private fun tint(iv: ImageView): ImageView = Ui.tint(this, iv)

    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    private val orbKeys = setOf(
        Prefs.K_OPACITY, Prefs.K_ORB_SCALE, Prefs.K_ORB_ICON, Prefs.K_ORB_SHAPE, Prefs.K_ACCENT
    )

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in orbKeys && scrim == null && orb != null) {
            handler.post { rebuildOrb() }
        }
    }

    private val fade = Runnable {
        orb?.animate()?.alpha(idleAlpha())?.setDuration(anim(400))?.start()
        peekOrb()
    }
    private val tapRunnable = Runnable {
        pendingTap = false
        runGesture(Prefs.tapAction(this))
    }
    private val longRunnable = Runnable {
        longFired = true
        haptic(orb)
        runGesture(Prefs.longAction(this))
    }
    private val unhide = Runnable { showOrb() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        running = true
        instance = this
        Prefs.of(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow “Display over other apps” first")
            stopSelf()
            return START_NOT_STICKY
        }
        if (orb == null) addOrb()
        when (intent?.action) {
            ACTION_TOGGLE -> if (hidden) showOrb() else hideOrb()
            ACTION_HIDE -> hideOrb()
            ACTION_SHOW -> showOrb()
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- notification

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Floating assistant", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_orb)
            .setContentTitle(if (hidden) "NavAssist orb is hidden" else "NavAssist is active")
            .setContentText(if (hidden) "Tap “Show orb” to bring it back" else "Tap the floating orb to open your buttons")
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, if (hidden) R.drawable.ic_orb else R.drawable.ic_visibility_off),
                    if (hidden) "Show orb" else "Hide orb", toggle
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_close), "Stop", stop
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification())
    }

    // ---------------------------------------------------------------- orb

    private fun screen(): Rect = wm.currentWindowMetrics.bounds
    private fun idleAlpha(): Float = Prefs.opacity(this) / 100f
    private fun anim(base: Long): Long =
        (base * (2f - 1.5f * Prefs.animSpeed(this) / 100f)).toLong().coerceAtLeast(30L)

    private fun haptic(v: View?) {
        if (Prefs.haptics(this)) v?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fade)
        handler.postDelayed(fade, 2500)
    }

    private fun applyVisibility() {
        val show = !hidden && !recHidden && !tempHidden && scrim == null
        orb?.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun buildOrbView(size: Int): FrameLayout {
        val box = Ui.orbView(this, size)
        box.contentDescription = "NavAssist menu"
        box.alpha = idleAlpha()
        return box
    }

    private fun rebuildOrb() {
        val old = orb
        val oldLp = orbLp
        if (old != null) runCatching { wm.removeView(old) }
        orb = null
        orbLp = null
        peeked = false
        addOrb(oldLp?.x, oldLp?.y)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOrb(keepX: Int? = null, keepY: Int? = null) {
        val size = dp(48) * Prefs.orbScale(this) / 100
        val b = screen()
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val savedX = keepX ?: Prefs.orbX(this)
        val savedY = keepY ?: Prefs.orbY(this)
        lp.x = if (savedX >= 0) savedX.coerceIn(0, b.width() - size) else b.width() - size - dp(4)
        lp.y = if (savedY >= 0) savedY.coerceIn(0, b.height() - size) else b.height() / 3
        dockX = if (lp.x + size / 2 < b.width() / 2) dp(4) else b.width() - size - dp(4)

        val box = buildOrbView(size)
        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        box.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var dragging = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        handler.removeCallbacks(fade)
                        v.animate().cancel()
                        v.alpha = 1f
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(anim(90)).start()
                        if (peeked) {
                            lp.x = dockX
                            peeked = false
                            wm.updateViewLayout(v, lp)
                        }
                        startX = lp.x
                        startY = lp.y
                        touchX = e.rawX
                        touchY = e.rawY
                        dragging = false
                        longFired = false
                        handler.postDelayed(longRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - touchX
                        val dy = e.rawY - touchY
                        if (!dragging && hypot(dx, dy) > slop) {
                            dragging = true
                            handler.removeCallbacks(longRunnable)
                        }
                        if (dragging) {
                            val bb = screen()
                            lp.x = (startX + dx.toInt()).coerceIn(0, bb.width() - size)
                            lp.y = (startY + dy.toInt()).coerceIn(0, bb.height() - size)
                            wm.updateViewLayout(v, lp)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longRunnable)
                        v.animate().scaleX(1f).scaleY(1f).setDuration(anim(120)).start()
                        if (dragging) {
                            snap(v, lp)
                        } else if (e.actionMasked == MotionEvent.ACTION_UP && !longFired) {
                            onTap()
                        }
                        scheduleFade()
                    }
                }
                return true
            }
        })

        wm.addView(box, lp)
        orb = box
        orbLp = lp
        applyVisibility()
        scheduleFade()
    }

    private fun animateX(v: View, lp: WindowManager.LayoutParams, target: Int, ms: Long) {
        val a = ValueAnimator.ofInt(lp.x, target)
        a.duration = ms
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            lp.x = it.animatedValue as Int
            if (v.isAttachedToWindow) wm.updateViewLayout(v, lp)
        }
        a.start()
    }

    private fun snap(v: View, lp: WindowManager.LayoutParams) {
        val b = screen()
        peeked = false
        if (!Prefs.snapToEdge(this)) {
            dockX = lp.x
            Prefs.setOrb(this, lp.x, lp.y)
            return
        }
        val target = if (lp.x + lp.width / 2 < b.width() / 2) dp(4) else b.width() - lp.width - dp(4)
        dockX = target
        animateX(v, lp, target, anim(220))
        Prefs.setOrb(this, target, lp.y)
    }

    /** Slides the idle orb half off the screen edge so it covers less of the screen. */
    private fun peekOrb() {
        val v = orb ?: return
        val lp = orbLp ?: return
        if (!Prefs.peek(this) || !Prefs.snapToEdge(this) || scrim != null || peeked || !v.isAttachedToWindow) return
        val b = screen()
        val target = if (dockX + lp.width / 2 < b.width() / 2) -(lp.width * 0.55f).toInt()
        else b.width() - (lp.width * 0.45f).toInt()
        peeked = true
        animateX(v, lp, target, anim(300))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val v = orb
        val lp = orbLp
        if (v != null && lp != null && scrim == null && v.isAttachedToWindow) {
            val b = screen()
            peeked = false
            lp.x = lp.x.coerceIn(0, b.width() - lp.width)
            lp.y = lp.y.coerceIn(0, b.height() - lp.height)
            wm.updateViewLayout(v, lp)
            snap(v, lp)
        }
    }

    // ---------------------------------------------------------------- gestures

    private fun onTap() {
        haptic(orb)
        val dbl = Prefs.doubleAction(this)
        if (dbl == "NONE") {
            runGesture(Prefs.tapAction(this))
            return
        }
        if (pendingTap) {
            handler.removeCallbacks(tapRunnable)
            pendingTap = false
            runGesture(dbl)
        } else {
            pendingTap = true
            handler.postDelayed(tapRunnable, ViewConfiguration.getDoubleTapTimeout().toLong())
        }
    }

    private fun runGesture(code: String) {
        when (code) {
            "MENU" -> expand()
            "HIDE" -> hideOrb()
            "NONE" -> {}
            else -> {
                val a = try {
                    NavAction.valueOf(code)
                } catch (e: IllegalArgumentException) {
                    null
                } ?: return
                if (a == NavAction.MIXER || a == NavAction.BRIGHTNESS || a == NavAction.FAVORITE) {
                    expand()
                    scrim?.let { showCard(it, subCard(a)) }
                } else {
                    onAction(a)
                }
            }
        }
    }

    private fun hideOrb() {
        hidden = true
        hiddenFlag = true
        handler.removeCallbacks(unhide)
        val s = Prefs.hideSeconds(this)
        if (s > 0) handler.postDelayed(unhide, s * 1000L)
        applyVisibility()
        updateNotification()
        toast(
            if (s > 0) "Orb hidden for $s seconds"
            else "Orb hidden. Use the notification or the Quick Settings tile to bring it back"
        )
    }

    private fun showOrb() {
        hidden = false
        hiddenFlag = false
        handler.removeCallbacks(unhide)
        applyVisibility()
        updateNotification()
        orb?.alpha = 1f
        scheduleFade()
    }

    /** Called by RecordService so the orb does not appear in screen recordings. */
    fun onRecordingState(recording: Boolean) {
        handler.post {
            recHidden = recording && Prefs.recHideOrb(this)
            applyVisibility()
        }
    }

    // ---------------------------------------------------------------- menu window

    private fun expand(initial: (() -> View)? = null) {
        if (scrim != null) return
        val s = FrameLayout(this)
        s.setBackgroundColor(0x70060A14.toInt())
        s.setOnClickListener { collapse() }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            PixelFormat.TRANSLUCENT
        )
        lp.blurBehindRadius = dp(24)
        menuPage = 0
        showCard(s, initial?.invoke() ?: menuCard())
        wm.addView(s, lp)
        scrim = s
        applyVisibility()
        s.alpha = 0f
        s.animate().alpha(1f).setDuration(anim(150)).start()
    }

    private fun collapse() {
        sliderTouch = false
        handler.removeCallbacks(mixerTick)
        mixerRows.clear()
        scrim?.let { runCatching { wm.removeView(it) } }
        scrim = null
        applyVisibility()
        orb?.alpha = 1f
        scheduleFade()
    }

    private fun showCard(parent: FrameLayout, card: View) {
        parent.removeAllViews()
        parent.addView(card, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        card.scaleX = 0.94f
        card.scaleY = 0.94f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(anim(150)).start()
    }

    /** Card that detects a horizontal swipe (to flip menu pages) without stealing taps from its buttons. */
    private inner class SwipeCard(c: Context, val onSwipe: (Int) -> Unit) : LinearLayout(c) {
        private var downX = 0f
        private var downY = 0f
        private var downT = 0L

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    downT = ev.eventTime
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (abs(dx) > dp(56) && abs(dx) > abs(dy) * 1.5f && ev.eventTime - downT < 600) {
                        onSwipe(if (dx < 0) 1 else -1)
                    }
                }
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    private fun baseCard(swipe: ((Int) -> Unit)? = null): LinearLayout {
        val card = if (swipe != null) SwipeCard(this, swipe) else LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.menuPanelBackground(this)
        card.setPadding(dp(14), dp(10), dp(14), dp(14))
        card.isClickable = true
        return card
    }

    private fun subHeader(card: LinearLayout, title: String) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        tint(back)
        back.setPadding(dp(9), dp(9), dp(9), dp(9))
        back.contentDescription = "Back to menu"
        back.setOnClickListener {
            handler.removeCallbacks(mixerTick)
            mixerRows.clear()
            scrim?.let { showCard(it, menuCard()) }
        }
        head.addView(back, LinearLayout.LayoutParams(dp(38), dp(38)))
        val t = Ui.text(this, title, 15f, th.text, weight = 600)
        t.setPadding(dp(6), 0, 0, 0)
        head.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        card.addView(head)
    }

    private fun tileWidth(cols: Int): Int =
        minOf(dp(78), (screen().width() - dp(24) - dp(28) - dp(16)) / cols)

    private fun menuCard(): View {
        val all = Prefs.enabledActions(this)
        val cols = Prefs.menuCols(this)
        val perPage = cols * Prefs.menuRows(this)
        val pages = ((all.size + perPage - 1) / perPage).coerceAtLeast(1)
        menuPage = menuPage.coerceIn(0, pages - 1)
        val card = baseCard { dir ->
            val np = (menuPage + dir).coerceIn(0, pages - 1)
            if (np != menuPage) {
                menuPage = np
                scrim?.let { showCard(it, menuCard()) }
            }
        }

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_orb)
        tint(logo)
        header.addView(logo, LinearLayout.LayoutParams(dp(22), dp(22)))
        val title = Ui.text(this, "NavAssist", 15f, th.text, weight = 600)
        title.setPadding(dp(10), 0, 0, 0)
        header.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        val close = ImageView(this)
        close.setImageResource(R.drawable.ic_close)
        tint(close)
        close.setPadding(dp(9), dp(9), dp(9), dp(9))
        close.alpha = 0.7f
        close.contentDescription = "Close menu"
        close.setOnClickListener { collapse() }
        header.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
        card.addView(header)

        val tileW = tileWidth(cols)
        val slice = all.drop(menuPage * perPage).take(perPage)
        for (rowActions in slice.chunked(cols)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (a in rowActions) row.addView(tile(a, tileW))
            repeat(cols - rowActions.size) { row.addView(View(this), LinearLayout.LayoutParams(tileW, 1)) }
            card.addView(row)
        }

        if (pages > 1) {
            val dots = LinearLayout(this)
            dots.orientation = LinearLayout.HORIZONTAL
            dots.gravity = Gravity.CENTER
            for (p in 0 until pages) {
                val d = View(this)
                d.background = Ui.oval(if (p == menuPage) Themes.accent(this) else Themes.withAlpha(th.text, 30))
                val dl = LinearLayout.LayoutParams(dp(7), dp(7))
                dl.setMargins(dp(4), dp(8), dp(4), 0)
                dots.addView(d, dl)
                d.setOnClickListener {
                    menuPage = p
                    scrim?.let { showCard(it, menuCard()) }
                }
            }
            card.addView(dots, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        return card
    }

    private fun tile(a: NavAction, w: Int): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(0, dp(7), 0, dp(5))
        t.layoutParams = LinearLayout.LayoutParams(w, WRAP)

        val accent = a == NavAction.AI
        val chipBg = Ui.menuChipBg(this, accent)
        val chip = ImageView(this)
        chip.setImageResource(a.icon)
        if (!accent) tint(chip)
        chip.setPadding(dp(14), dp(14), dp(14), dp(14))
        chip.background = Ui.ripple(chipBg, dp(16).toFloat())
        t.addView(chip, LinearLayout.LayoutParams(dp(52), dp(52)))

        val label = Ui.text(this, a.label, 11f, Themes.withAlpha(th.text, 88), weight = 500)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.setPadding(0, dp(5), 0, 0)
        t.addView(label)

        t.contentDescription = a.label
        t.setOnClickListener {
            haptic(it)
            onAction(a)
        }
        return t
    }

    private fun subCard(a: NavAction): View = when (a) {
        NavAction.MIXER -> mixerCard()
        NavAction.BRIGHTNESS -> brightnessCard()
        else -> favoritesCard()
    }

    private fun pill(text: String, onClick: () -> Unit): View {
        val t = Ui.text(this, text, 13f, Color.WHITE, weight = 600)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(9), dp(16), dp(9))
        t.background = Ui.ripple(Ui.shape(Themes.accent(this), dp(10).toFloat()), dp(10).toFloat())
        t.setOnClickListener { onClick() }
        return t
    }

    private fun slider(min: Int, max: Int, value: Int): SeekBar {
        val sb = SeekBar(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())
        val acc = Themes.accent(this)
        sb.min = min
        sb.max = max
        sb.progress = value
        sb.progressTintList = ColorStateList(states, intArrayOf(acc, acc))
        sb.thumbTintList = ColorStateList(states, intArrayOf(if (th.light) acc else Color.WHITE, if (th.light) acc else Color.WHITE))
        sb.progressBackgroundTintList = ColorStateList.valueOf(Themes.withAlpha(th.text, 25))
        return sb
    }

    // ---------------------------------------------------------------- recent apps panel

    /** NavAssist's own recent-tabs screen, used when the phone ignores the system Recents action. */
    fun showRecentsPanel() {
        handler.post {
            if (scrim == null) expand { recentsCard() } else scrim?.let { showCard(it, recentsCard()) }
        }
    }

    private fun recentsCard(): View {
        val card = baseCard()
        card.minimumWidth = minOf(dp(320), screen().width() - dp(40))
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_history)
        tint(icon)
        head.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
        val title = Ui.text(this, "Recent apps", 15f, th.text, weight = 600)
        title.setPadding(dp(10), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        val close = ImageView(this)
        close.setImageResource(R.drawable.ic_close)
        tint(close)
        close.setPadding(dp(9), dp(9), dp(9), dp(9))
        close.alpha = 0.7f
        close.setOnClickListener { collapse() }
        head.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
        card.addView(head)

        val apps = RecentApps.list(this, 12)
        if (apps.isEmpty()) {
            val n = Ui.text(this, "No recent apps yet.\nOpen a few apps, or allow Usage access so NavAssist can see your history.", 13f, th.muted)
            n.setPadding(dp(6), dp(14), dp(6), dp(12))
            card.addView(n)
            card.addView(pill("Allow usage access") {
                collapse()
                handler.postDelayed({ safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, 250)
            }, LinearLayout.LayoutParams(WRAP, WRAP))
            return card
        }
        val tileW = tileWidth(3)
        for (rowApps in apps.chunked(3)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (pkg in rowApps) {
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                cell.setPadding(0, dp(8), 0, dp(6))
                val iv = ImageView(this)
                try {
                    iv.setImageDrawable(packageManager.getApplicationIcon(pkg))
                } catch (e: Exception) {
                    iv.setImageResource(R.drawable.ic_apps)
                }
                cell.addView(iv, LinearLayout.LayoutParams(dp(52), dp(52)))
                val label = Ui.text(this, Apps.label(this, pkg) ?: pkg, 11f, Themes.withAlpha(th.text, 88), weight = 500)
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.setPadding(dp(2), dp(5), dp(2), 0)
                cell.addView(label)
                cell.layoutParams = LinearLayout.LayoutParams(tileW, WRAP)
                cell.background = Ui.ripple(android.graphics.drawable.ColorDrawable(0), dp(14).toFloat())
                cell.setOnClickListener {
                    haptic(it)
                    collapse()
                    handler.postDelayed({ if (!Apps.launch(this, pkg)) toast("Could not open the app") }, 250)
                }
                row.addView(cell)
            }
            repeat(3 - rowApps.size) { row.addView(View(this), LinearLayout.LayoutParams(tileW, 1)) }
            card.addView(row)
        }
        return card
    }

    // ---------------------------------------------------------------- favorites page

    private fun favoritesCard(): View {
        val card = baseCard()
        subHeader(card, "Favorite apps")
        val favs = Prefs.favorites(this).take(12)
        if (favs.isEmpty()) {
            val n = Ui.text(this, "No favorites yet.\nChoose them in NavAssist → Menu → Favorite apps.", 13f, th.muted)
            n.setPadding(dp(8), dp(16), dp(8), dp(8))
            card.addView(n)
            return card
        }
        val tileW = tileWidth(3)
        for (rowApps in favs.chunked(3)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (pkg in rowApps) {
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                cell.setPadding(0, dp(8), 0, dp(6))
                val icon = ImageView(this)
                try {
                    icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
                } catch (e: Exception) {
                    icon.setImageResource(R.drawable.ic_apps)
                }
                cell.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
                val label = Ui.text(this, Apps.label(this, pkg) ?: pkg, 11f, Themes.withAlpha(th.text, 88), weight = 500)
                label.gravity = Gravity.CENTER
                label.maxLines = 1
                label.setPadding(dp(2), dp(5), dp(2), 0)
                cell.addView(label)
                cell.layoutParams = LinearLayout.LayoutParams(tileW, WRAP)
                cell.setOnClickListener {
                    haptic(it)
                    collapse()
                    handler.postDelayed({ if (!Apps.launch(this, pkg)) toast("Could not open the app") }, 250)
                }
                row.addView(cell)
            }
            repeat(3 - rowApps.size) { row.addView(View(this), LinearLayout.LayoutParams(tileW, 1)) }
            card.addView(row)
        }
        return card
    }

    // ---------------------------------------------------------------- brightness page

    private fun openWriteSettings() {
        safeStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun brightnessCard(): View {
        val card = baseCard()
        card.minimumWidth = minOf(dp(320), screen().width() - dp(40))
        subHeader(card, "Brightness")
        if (!Settings.System.canWrite(this)) {
            val n = Ui.text(this, "Allow “Modify system settings” once to change brightness from here.", 13f, th.muted)
            n.setPadding(dp(6), dp(12), dp(6), dp(12))
            card.addView(n)
            card.addView(pill("Allow") { collapse(); openWriteSettings() }, LinearLayout.LayoutParams(WRAP, WRAP))
            return card
        }
        val cr = contentResolver
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(4), dp(12), dp(4), dp(4))
        val ic = ImageView(this)
        ic.setImageResource(R.drawable.ic_brightness)
        tint(ic)
        row.addView(ic, LinearLayout.LayoutParams(dp(22), dp(22)))
        val sb = slider(1, 255, cur.coerceIn(1, 255))
        val sl = LinearLayout.LayoutParams(0, WRAP, 1f)
        sl.marginStart = dp(12)
        row.addView(sb, sl)
        card.addView(row)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                try {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, p)
                } catch (e: Exception) {
                    toast("Could not change brightness")
                }
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        val auto = pill("Auto brightness") {
            try {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                toast("Auto brightness on")
            } catch (e: Exception) {
                toast("Could not change brightness")
            }
        }
        val al = LinearLayout.LayoutParams(WRAP, WRAP)
        al.topMargin = dp(10)
        card.addView(auto, al)
        return card
    }

    // ---------------------------------------------------------------- volume mixer

    private val mixerRows = ArrayList<Triple<SeekBar, TextView, Int>>() // seekbar, percent label, stream
    private val mixerTick = object : Runnable {
        override fun run() {
            if (scrim == null || mixerRows.isEmpty()) return
            val am = getSystemService(AudioManager::class.java)
            if (!sliderTouch) {
                for ((sb, pct, stream) in mixerRows) {
                    val v = am.getStreamVolume(stream)
                    if (sb.progress != v) sb.progress = v
                    pct.text = pctText(am, stream, v)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun pctText(am: AudioManager, stream: Int, v: Int): String {
        val mn = am.getStreamMinVolume(stream)
        val range = (am.getStreamMaxVolume(stream) - mn).coerceAtLeast(1)
        return "${((v - mn) * 100 / range).coerceIn(0, 100)}%"
    }

    /**
     * Sets a stream's volume. Some phones ignore setStreamVolume from other apps, so if the value did not
     * change we nudge it step by step with adjustStreamVolume (what the volume keys do).
     */
    private fun setVolumeSafe(am: AudioManager, stream: Int, target: Int): Boolean {
        try {
            am.setStreamVolume(stream, target, 0)
        } catch (e: SecurityException) {
            return false
        }
        var cur = am.getStreamVolume(stream)
        var guard = 0
        while (cur != target && guard < 25) {
            try {
                am.adjustStreamVolume(stream, if (target > cur) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            } catch (e: SecurityException) {
                break
            }
            val next = am.getStreamVolume(stream)
            if (next == cur) break
            cur = next
            guard++
        }
        return cur == target
    }

    private fun hasDnd(): Boolean =
        getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun mixerCard(): View {
        val am = getSystemService(AudioManager::class.java)
        val card = baseCard()
        card.minimumWidth = minOf(dp(330), screen().width() - dp(40))
        mixerRows.clear()
        subHeader(card, "Volume mixer")
        val acc = Themes.accent(this)

        val modes = LinearLayout(this)
        modes.orientation = LinearLayout.HORIZONTAL
        val modeDefs = listOf(
            "Sound" to AudioManager.RINGER_MODE_NORMAL,
            "Vibrate" to AudioManager.RINGER_MODE_VIBRATE,
            "Silent" to AudioManager.RINGER_MODE_SILENT
        )
        val modeViews = ArrayList<Pair<TextView, Int>>()
        fun paintModes() {
            for ((v, m) in modeViews) {
                val on = am.ringerMode == m
                v.background = Ui.shape(
                    if (on) acc else Themes.withAlpha(th.text, 12), dp(12).toFloat(),
                    if (on) acc else Themes.withAlpha(th.text, 16), dp(1)
                )
                v.setTextColor(if (on) Color.WHITE else th.muted)
            }
        }
        for ((name, mode) in modeDefs) {
            val v = Ui.text(this, name, 12.5f, th.muted, weight = 500)
            v.gravity = Gravity.CENTER
            v.setPadding(0, dp(9), 0, dp(9))
            v.setOnClickListener {
                try {
                    am.ringerMode = mode
                } catch (e: SecurityException) {
                    toast("Silent needs “Do Not Disturb access” (NavAssist → Setup)")
                }
                paintModes()
            }
            modeViews.add(v to mode)
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(dp(3), dp(8), dp(3), dp(4))
            modes.addView(v, lp)
        }
        paintModes()
        card.addView(modes)

        val streams = listOf(
            Triple("Media  (YouTube, music)", AudioManager.STREAM_MUSIC, R.drawable.ic_volume_up),
            Triple("Call / meeting", AudioManager.STREAM_VOICE_CALL, R.drawable.ic_call),
            Triple("Ringtone", AudioManager.STREAM_RING, R.drawable.ic_notifications),
            Triple("Notifications", AudioManager.STREAM_NOTIFICATION, R.drawable.ic_notifications),
            Triple("Alarm", AudioManager.STREAM_ALARM, R.drawable.ic_alarm)
        )
        for ((name, stream, icon) in streams) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(4), dp(8), dp(4), dp(2))
            val ic = ImageView(this)
            ic.setImageResource(icon)
            tint(ic)
            ic.alpha = 0.9f
            val icLp = LinearLayout.LayoutParams(dp(22), dp(22))
            icLp.marginEnd = dp(12)
            row.addView(ic, icLp)

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val top = LinearLayout(this)
            top.orientation = LinearLayout.HORIZONTAL
            val pct = Ui.text(this, "", 12f, th.muted)
            top.addView(Ui.text(this, name, 13f, th.text, weight = 500), LinearLayout.LayoutParams(0, WRAP, 1f))
            top.addView(pct)
            col.addView(top)

            val sb = slider(am.getStreamMinVolume(stream), am.getStreamMaxVolume(stream), am.getStreamVolume(stream))
            pct.text = pctText(am, stream, sb.progress)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    if (!setVolumeSafe(am, stream, p)) toast("Allow “Do Not Disturb access” in NavAssist → Setup to change this")
                    pct.text = pctText(am, stream, am.getStreamVolume(stream))
                }

                override fun onStartTrackingTouch(s: SeekBar) { sliderTouch = true }
                override fun onStopTrackingTouch(s: SeekBar) {
                    sliderTouch = false
                    s.progress = am.getStreamVolume(stream)
                    pct.text = pctText(am, stream, s.progress)
                }
            })
            mixerRows.add(Triple(sb, pct, stream))
            col.addView(sb, LinearLayout.LayoutParams(MATCH, WRAP))
            row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            card.addView(row)
        }

        val note = Ui.text(
            this,
            "Meetings use “Call” volume; YouTube and music use “Media”. " +
                "Android does not let third-party apps change volume per individual app." +
                if (hasDnd()) "" else "\nRingtone and Silent need Do Not Disturb access (NavAssist → Setup).",
            11f, th.muted
        )
        note.setPadding(dp(4), dp(10), dp(4), 0)
        note.maxWidth = minOf(dp(330), screen().width() - dp(40)) - dp(28)
        card.addView(note)
        card.addView(pill("Open system volume panel") { collapse(); handler.postDelayed({ safeStart(Intent(Settings.Panel.ACTION_VOLUME)) }, 250) },
            LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = dp(10) })
        handler.removeCallbacks(mixerTick)
        handler.postDelayed(mixerTick, 500)
        return card
    }

    // ---------------------------------------------------------------- actions

    private fun onAction(a: NavAction) {
        when (a) {
            NavAction.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
            NavAction.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
            NavAction.TORCH -> toggleTorch()
            NavAction.TIMEOUT -> cycleTimeout()
            NavAction.ROTATION -> toggleRotation()
            NavAction.SOUND_MODE -> cycleSound()
            NavAction.MIXER, NavAction.BRIGHTNESS, NavAction.FAVORITE ->
                scrim?.let { showCard(it, subCard(a)) } ?: runGesture(a.name)
            else -> {
                collapse()
                if (a == NavAction.SCREENSHOT) {
                    // hide the orb so it is not captured in the screenshot
                    tempHidden = true
                    applyVisibility()
                    handler.postDelayed({ perform(a) }, 450)
                    handler.postDelayed({ tempHidden = false; applyVisibility() }, 1800)
                } else {
                    handler.postDelayed({ perform(a) }, 300)
                }
            }
        }
    }

    private fun perform(a: NavAction) {
        when (a) {
            NavAction.HOME -> safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            NavAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            NavAction.RECENTS -> {
                val svc = NavAccessibilityService.instance
                if (svc != null) svc.openRecents(Prefs.recentsMode(this)) else showRecentsPanel()
            }
            NavAction.RECENT_APPS -> showRecentsPanel()
            NavAction.NOTIFICATIONS -> withService { it.openNotifications() }
            NavAction.QUICK_SETTINGS -> withService { it.openQuickSettings() }
            NavAction.LOCK -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            NavAction.POWER -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            NavAction.SCREENSHOT -> global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            NavAction.SEARCH -> safeStart(Intent(this, SearchActivity::class.java))
            NavAction.AI -> {
                val pkg = Prefs.aiPkg(this)
                if (AiConfig.load(this).ready || pkg == null) {
                    safeStart(Intent(this, AiChatActivity::class.java))
                } else {
                    launchPkg(pkg, Intent(Intent.ACTION_ASSIST))
                }
            }
            NavAction.CAMERA -> safeStart(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            NavAction.SELFIE -> safeStart(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .putExtra("android.intent.extras.CAMERA_FACING", 1)
                    .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                    .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            )
            NavAction.GALLERY -> safeStart(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
            )
            NavAction.WIFI -> safeStart(Intent(Settings.Panel.ACTION_WIFI))
            NavAction.DATA -> safeStart(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            NavAction.BLUETOOTH -> safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            NavAction.LOCATION -> safeStart(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            NavAction.AIRPLANE -> safeStart(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            NavAction.SETTINGS -> safeStart(Intent(Settings.ACTION_SETTINGS))
            NavAction.STORAGE -> safeStart(Intent("android.os.storage.action.MANAGE_STORAGE"))
            NavAction.HIDE -> hideOrb()
            NavAction.RECORDER ->
                if (RecordService.recording) {
                    startService(Intent(this, RecordService::class.java).setAction(RecordService.ACTION_STOP))
                } else {
                    safeStart(Intent(this, RecordActivity::class.java))
                }
            else -> {}
        }
    }

    private fun global(action: Int) = withService { it.run(action) }

    private fun withService(block: (NavAccessibilityService) -> Unit) {
        val svc = NavAccessibilityService.instance
        if (svc == null) {
            toast("Turn NavAssist on in Accessibility settings first")
        } else {
            block(svc)
        }
    }

    private fun volume(direction: Int) {
        val am = getSystemService(AudioManager::class.java)
        val inCall = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
        try {
            if (inCall) {
                am.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, direction, AudioManager.FLAG_SHOW_UI)
            } else {
                am.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            }
        } catch (e: SecurityException) {
            toast("Volume could not be changed")
        }
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                toast("No flashlight found")
                return
            }
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
        } catch (e: Exception) {
            toast("Flashlight unavailable")
        }
    }

    private fun cycleTimeout() {
        if (!Settings.System.canWrite(this)) {
            collapse()
            toast("Allow “Modify system settings” for NavAssist first")
            openWriteSettings()
            return
        }
        val steps = listOf(15000, 30000, 60000, 120000, 300000, 600000)
        val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
        val next = steps.firstOrNull { it > cur } ?: steps[0]
        try {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, next)
            toast("Screen timeout: " + if (next < 60000) "${next / 1000} seconds" else "${next / 60000} min")
        } catch (e: Exception) {
            toast("Could not change screen timeout")
        }
    }

    private fun toggleRotation() {
        if (!Settings.System.canWrite(this)) {
            collapse()
            toast("Allow “Modify system settings” for NavAssist first")
            openWriteSettings()
            return
        }
        val on = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        try {
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)
            toast(if (on) "Rotation locked" else "Auto-rotate on")
        } catch (e: Exception) {
            toast("Could not change rotation")
        }
    }

    private fun cycleSound() {
        val am = getSystemService(AudioManager::class.java)
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try {
            am.ringerMode = next
            toast(when (next) {
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                AudioManager.RINGER_MODE_SILENT -> "Silent"
                else -> "Sound on"
            })
        } catch (e: SecurityException) {
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            toast("Silent needs “Do Not Disturb access” (NavAssist → Setup)")
        }
    }

    private fun launchPkg(pkg: String?, fallback: Intent?) {
        if (pkg != null && Apps.launch(this, pkg)) return
        if (fallback != null) safeStart(fallback) else toast("Choose an app for this button in NavAssist")
    }

    private fun safeStart(i: Intent) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            toast("Not available on this phone")
        }
    }

    // ---------------------------------------------------------------- misc

    private fun dp(v: Int): Int = Ui.dp(this, v)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scrim?.let { runCatching { wm.removeView(it) } }
        orb?.let { runCatching { wm.removeView(it) } }
        scrim = null
        orb = null
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        running = false
        hiddenFlag = false
        instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.navassist.STOP"
        const val ACTION_TOGGLE = "com.example.navassist.TOGGLE"
        const val ACTION_HIDE = "com.example.navassist.HIDE"
        const val ACTION_SHOW = "com.example.navassist.SHOW"
        private const val CHANNEL_ID = "overlay_channel"

        @Volatile
        var running = false

        @Volatile
        var hiddenFlag = false

        @Volatile
        var instance: OverlayService? = null
    }
}
