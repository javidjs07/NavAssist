package com.example.navassist

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
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
import kotlin.math.hypot

/**
 * Foreground service that draws the draggable floating orb and, when tapped,
 * a glass-style menu with all the navigation / utility buttons.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var orb: ImageView? = null
    private var orbLp: WindowManager.LayoutParams? = null
    private var scrim: FrameLayout? = null
    private var torchOn = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.K_OPACITY && scrim == null) {
            orb?.alpha = idleAlpha()
        } else if (key == Prefs.K_ORB_SIZE && scrim == null && orb != null) {
            handler.post {
                orb?.let { runCatching { wm.removeView(it) } }
                orb = null
                addOrb()
            }
        }
    }

    private val fade = Runnable {
        orb?.animate()?.alpha(idleAlpha())?.setDuration(400)?.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        running = true
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
        return START_STICKY
    }

    // ---------------------------------------------------------------- notification

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Floating assistant", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_orb)
            .setContentTitle("NavAssist is active")
            .setContentText("Tap the floating orb to open your buttons")
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_close), "Stop", stop
                ).build()
            )
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    // ---------------------------------------------------------------- orb

    private fun screen(): Rect = wm.currentWindowMetrics.bounds

    private fun idleAlpha(): Float = Prefs.opacity(this) / 100f

    private fun scheduleFade() {
        handler.removeCallbacks(fade)
        handler.postDelayed(fade, 2500)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOrb() {
        val size = dp(when (Prefs.orbSize(this)) { 0 -> 40; 2 -> 58; else -> 48 })
        val b = screen()
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val savedX = Prefs.orbX(this)
        val savedY = Prefs.orbY(this)
        lp.x = if (savedX >= 0) savedX.coerceIn(0, b.width() - size) else b.width() - size - dp(4)
        lp.y = if (savedY >= 0) savedY.coerceIn(0, b.height() - size) else b.height() / 3

        val iv = ImageView(this)
        iv.setImageResource(R.drawable.ic_orb)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = size / 4
        iv.setPadding(pad, pad, pad, pad)
        iv.background = Ui.oval(0x990B0F19.toInt(), 0x996366F1.toInt(), dp(1))
        iv.contentDescription = "NavAssist menu"
        iv.alpha = idleAlpha()

        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        iv.setOnTouchListener(object : View.OnTouchListener {
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
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
                        startX = lp.x
                        startY = lp.y
                        touchX = e.rawX
                        touchY = e.rawY
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - touchX
                        val dy = e.rawY - touchY
                        if (!dragging && hypot(dx, dy) > slop) dragging = true
                        if (dragging) {
                            val bb = screen()
                            lp.x = (startX + dx.toInt()).coerceIn(0, bb.width() - size)
                            lp.y = (startY + dy.toInt()).coerceIn(0, bb.height() - size)
                            wm.updateViewLayout(v, lp)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (dragging) {
                            snap(v, lp)
                        } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                            if (Prefs.haptics(this@OverlayService)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            expand()
                        }
                        scheduleFade()
                    }
                }
                return true
            }
        })

        wm.addView(iv, lp)
        orb = iv
        orbLp = lp
        scheduleFade()
    }

    private fun snap(v: View, lp: WindowManager.LayoutParams) {
        val b = screen()
        if (!Prefs.snapToEdge(this)) {
            Prefs.setOrb(this, lp.x, lp.y)
            return
        }
        val target = if (lp.x + lp.width / 2 < b.width() / 2) dp(4) else b.width() - lp.width - dp(4)
        val anim = ValueAnimator.ofInt(lp.x, target)
        anim.duration = 220
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            lp.x = it.animatedValue as Int
            if (v.isAttachedToWindow) wm.updateViewLayout(v, lp)
        }
        anim.start()
        Prefs.setOrb(this, target, lp.y)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val v = orb
        val lp = orbLp
        if (v != null && lp != null && scrim == null && v.isAttachedToWindow) {
            val b = screen()
            lp.x = lp.x.coerceIn(0, b.width() - lp.width)
            lp.y = lp.y.coerceIn(0, b.height() - lp.height)
            wm.updateViewLayout(v, lp)
            snap(v, lp)
        }
    }

    // ---------------------------------------------------------------- menu window

    private fun expand() {
        if (scrim != null) return
        orb?.visibility = View.INVISIBLE
        val s = FrameLayout(this)
        s.setBackgroundColor(0x70060A14.toInt())
        s.setOnClickListener { collapse() }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        // Frosted-glass effect behind the menu (ignored on phones that disable window blur).
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        lp.blurBehindRadius = dp(24)
        showCard(s, menuCard())
        wm.addView(s, lp)
        scrim = s
        s.alpha = 0f
        s.animate().alpha(1f).setDuration(150).start()
    }

    private fun collapse() {
        mixerRows.clear()
        handler.removeCallbacks(mixerTick)
        scrim?.let { runCatching { wm.removeView(it) } }
        scrim = null
        orb?.let {
            it.visibility = View.VISIBLE
            it.alpha = 1f
        }
        scheduleFade()
    }

    private fun showCard(parent: FrameLayout, card: View) {
        parent.removeAllViews()
        parent.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start()
    }

    private fun baseCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.shape(0xE0111827.toInt(), dp(24).toFloat(), Ui.BORDER, dp(1))
        card.setPadding(dp(14), dp(10), dp(14), dp(14))
        card.isClickable = true
        return card
    }

    private fun menuCard(): View {
        val actions = Prefs.enabledActions(this)
        val card = baseCard()

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_orb)
        header.addView(logo, LinearLayout.LayoutParams(dp(22), dp(22)))
        val title = Ui.text(this, "NavAssist", 15f, Ui.TEXT, bold = true)
        title.setPadding(dp(10), 0, 0, 0)
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = ImageView(this)
        close.setImageResource(R.drawable.ic_close)
        close.setPadding(dp(9), dp(9), dp(9), dp(9))
        close.alpha = 0.7f
        close.contentDescription = "Close assistant"
        close.setOnClickListener { stopSelf() }
        header.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
        card.addView(header)

        val tileW = minOf(dp(72), (screen().width() - dp(24) - dp(28) - dp(16)) / 4)
        for (rowActions in actions.chunked(4)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (a in rowActions) row.addView(tile(a, tileW))
            repeat(4 - rowActions.size) {
                row.addView(View(this), LinearLayout.LayoutParams(tileW, 1))
            }
            card.addView(row)
        }
        return card
    }

    private fun tile(a: NavAction, w: Int): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(0, dp(7), 0, dp(5))
        t.layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)

        val accent = a == NavAction.AI
        val chipBg = if (accent) {
            Ui.shape(Ui.VIOLET, dp(16).toFloat())
        } else {
            Ui.shape(0xB31F2937.toInt(), dp(16).toFloat(), 0xFF2B3647.toInt(), dp(1))
        }
        val chip = ImageView(this)
        chip.setImageResource(a.icon)
        chip.setPadding(dp(14), dp(14), dp(14), dp(14))
        chip.background = Ui.ripple(chipBg, dp(16).toFloat())
        chip.isClickable = false
        t.addView(chip, LinearLayout.LayoutParams(dp(52), dp(52)))

        val label = Ui.text(this, a.label, 11f, Ui.MUTED, weight = 500)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.setPadding(0, dp(5), 0, 0)
        t.addView(label)

        t.contentDescription = a.label
        t.setOnClickListener {
            if (Prefs.haptics(this)) it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onAction(a)
        }
        return t
    }

    // ---------------------------------------------------------------- volume mixer

    private val mixerRows = ArrayList<Triple<SeekBar, TextView, Int>>() // seekbar, percent label, stream
    private var touching = false
    private val mixerTick = object : Runnable {
        override fun run() {
            if (scrim == null || mixerRows.isEmpty()) return
            val am = getSystemService(AudioManager::class.java)
            if (!touching) {
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

    private fun hasDnd(): Boolean =
        getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun mixerCard(): View {
        val am = getSystemService(AudioManager::class.java)
        val card = baseCard()
        card.minimumWidth = minOf(dp(330), screen().width() - dp(40))
        mixerRows.clear()

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setPadding(dp(9), dp(9), dp(9), dp(9))
        back.contentDescription = "Back to menu"
        back.setOnClickListener {
            mixerRows.clear()
            scrim?.let { showCard(it, menuCard()) }
        }
        head.addView(back, LinearLayout.LayoutParams(dp(38), dp(38)))
        val title = Ui.text(this, "Volume mixer", 15f, Ui.TEXT, weight = 600)
        title.setPadding(dp(6), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(head)

        // ringer mode: Sound / Vibrate / Silent
        val modes = LinearLayout(this)
        modes.orientation = LinearLayout.HORIZONTAL
        val modeDefs = listOf(
            Triple("Sound", AudioManager.RINGER_MODE_NORMAL, R.drawable.ic_notifications),
            Triple("Vibrate", AudioManager.RINGER_MODE_VIBRATE, R.drawable.ic_volume_down),
            Triple("Silent", AudioManager.RINGER_MODE_SILENT, R.drawable.ic_volume_off)
        )
        val modeViews = ArrayList<Pair<TextView, Int>>()
        fun paintModes() {
            for ((v, m) in modeViews) {
                val on = am.ringerMode == m
                v.background = Ui.shape(
                    if (on) Ui.VIOLET else 0xB31F2937.toInt(), dp(12).toFloat(),
                    if (on) Ui.VIOLET else 0xFF2B3647.toInt(), dp(1)
                )
                v.setTextColor(if (on) Color.WHITE else Ui.MUTED)
            }
        }
        for ((name, mode, _) in modeDefs) {
            val v = Ui.text(this, name, 12.5f, Ui.MUTED, weight = 500)
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
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
        val states = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())

        for ((name, stream, icon) in streams) {
            val minV = am.getStreamMinVolume(stream)
            val maxV = am.getStreamMaxVolume(stream)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(4), dp(8), dp(4), dp(2))
            val ic = ImageView(this)
            ic.setImageResource(icon)
            ic.alpha = 0.9f
            val icLp = LinearLayout.LayoutParams(dp(22), dp(22))
            icLp.marginEnd = dp(12)
            row.addView(ic, icLp)

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val top = LinearLayout(this)
            top.orientation = LinearLayout.HORIZONTAL
            val label = Ui.text(this, name, 13f, Ui.TEXT, weight = 500)
            val pct = Ui.text(this, "", 12f, Ui.MUTED)
            top.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(pct)
            col.addView(top)

            val sb = SeekBar(this)
            sb.min = minV
            sb.max = maxV
            sb.progress = am.getStreamVolume(stream)
            sb.progressTintList = ColorStateList(states, intArrayOf(Ui.VIOLET, Ui.VIOLET))
            sb.thumbTintList = ColorStateList(states, intArrayOf(Ui.CYAN, Ui.CYAN))
            sb.progressBackgroundTintList = ColorStateList.valueOf(0xFF2B3647.toInt())
            pct.text = pctText(am, stream, sb.progress)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    try {
                        am.setStreamVolume(stream, p, 0)
                    } catch (e: SecurityException) {
                        toast("Allow “Do Not Disturb access” in NavAssist → Setup to change this")
                    }
                    pct.text = pctText(am, stream, am.getStreamVolume(stream))
                }

                override fun onStartTrackingTouch(s: SeekBar) { touching = true }
                override fun onStopTrackingTouch(s: SeekBar) {
                    touching = false
                    // snap back to what the phone really accepted
                    s.progress = am.getStreamVolume(stream)
                    pct.text = pctText(am, stream, s.progress)
                }
            })
            mixerRows.add(Triple(sb, pct, stream))
            col.addView(sb, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(row)
        }

        val note = Ui.text(
            this,
            "Meetings use “Call” volume; YouTube and music use “Media”. " +
                "Android does not let third-party apps change volume per individual app." +
                if (hasDnd()) "" else "\nRingtone and Silent need Do Not Disturb access (NavAssist → Setup).",
            11f, Ui.MUTED
        )
        note.setPadding(dp(4), dp(10), dp(4), 0)
        card.addView(note)
        handler.removeCallbacks(mixerTick)
        handler.postDelayed(mixerTick, 500)
        return card
    }

    // ---------------------------------------------------------------- actions

    private fun onAction(a: NavAction) {
        when (a) {
            NavAction.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
            NavAction.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
            NavAction.MIXER -> scrim?.let { showCard(it, mixerCard()) }
            NavAction.TORCH -> toggleTorch()
            else -> {
                collapse()
                val delay = if (a == NavAction.SCREENSHOT) 450L else 300L
                handler.postDelayed({ perform(a) }, delay)
            }
        }
    }

    private fun perform(a: NavAction) {
        when (a) {
            NavAction.HOME -> safeStart(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            )
            NavAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            NavAction.RECENTS -> withService { it.openRecents(Prefs.recentsMode(this)) }
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
            NavAction.FAVORITE -> launchPkg(Prefs.favPkg(this), null)
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
            NavAction.DATA -> safeStart(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            NavAction.SETTINGS -> safeStart(Intent(Settings.ACTION_SETTINGS))
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
        val stream = if (inCall) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
        try {
            am.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
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
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.navassist.STOP"
        private const val CHANNEL_ID = "overlay_channel"

        @Volatile
        var running = false
    }
}
