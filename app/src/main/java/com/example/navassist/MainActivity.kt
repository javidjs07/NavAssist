package com.example.navassist

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {

    private class Page(val id: Int, val title: String, val icon: Int, val pic: Int, val subs: List<String> = emptyList())

    private val pages = listOf(
        Page(0, "Home", R.drawable.ic_home, R.drawable.i3d_home),
        Page(1, "Menu & gestures", R.drawable.ic_apps, R.drawable.i3d_menu, listOf("Layout", "Buttons", "Gestures", "Favorites")),
        Page(2, "Design", R.drawable.ic_palette, R.drawable.i3d_design, listOf("Theme", "Orb", "Motion")),
        Page(3, "Recording", R.drawable.ic_record, R.drawable.i3d_record, listOf("Record", "Library")),
        Page(4, "Cleaning", R.drawable.ic_cleaning, R.drawable.i3d_clean),
        Page(5, "Connections", R.drawable.ic_data, R.drawable.i3d_connect),
        Page(6, "AI assistant", R.drawable.ic_sparkle, R.drawable.i3d_ai),
        Page(7, "Features", R.drawable.ic_star, R.drawable.i3d_features),
        Page(8, "Settings", R.drawable.ic_settings, R.drawable.i3d_settings, listOf("Setup", "Advanced", "Test")),
        Page(9, "About us", R.drawable.ic_info, R.drawable.i3d_about)
    )

    private lateinit var scroll: ScrollView
    private lateinit var navBar: LinearLayout
    private lateinit var appBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var statusChip: TextView
    private lateinit var drawerScrim: FrameLayout
    private lateinit var drawerPanel: ScrollView
    private val navPills = ArrayList<FrameLayout>()
    private val navIcons = ArrayList<ImageView>()
    private val navLabels = ArrayList<TextView>()
    private val drawerRows = ArrayList<Pair<Int, LinearLayout>>()
    private val handler = Handler(Looper.getMainLooper())
    private val scenes = HashMap<String, WebView>()
    private var lastSig = ""
    private var page = 0
    private val sub = IntArray(10)
    private var gestureSel = 0
    private var drawerOpen = false
    private var drawerWidth = 0
    private var previewHolder: FrameLayout? = null
    private var previewBuilder: (() -> View)? = null

    // cleaning page state
    private var scanShots: List<Cleaner.Media> = emptyList()
    private var scanVideos: List<Cleaner.Media> = emptyList()
    private val picked = HashSet<Long>()
    private var scanned = false

    private val ticker = object : Runnable {
        override fun run() {
            refresh(false)
            handler.postDelayed(this, 1200)
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    private val DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert

    private val voiceLangs = listOf(
        "Device default" to "", "English (India)" to "en-IN", "English (US)" to "en-US",
        "తెలుగు" to "te-IN", "हिन्दी" to "hi-IN", "தமிழ்" to "ta-IN", "ಕನ್ನಡ" to "kn-IN"
    )
    private val recentsModes = listOf("Auto", "System", "Swipe", "Launcher")
    private val resolutions = listOf("Auto", "1080p", "720p", "480p")
    private val bitrates = listOf("4 Mbps", "8 Mbps", "12 Mbps")
    private val fpsList = listOf("30 fps", "60 fps")
    private val sounds = listOf("Mute", "Microphone")
    private val hideOptions = listOf(5 to "5 s", 15 to "15 s", 30 to "30 s", 60 to "1 min", 0 to "Until shown")

    private val gestureOptions: List<Pair<String, String>> by lazy {
        listOf("MENU" to "Open menu", "HIDE" to "Hide orb", "NONE" to "Do nothing") +
            NavAction.values().filter { it != NavAction.HIDE }.map { it.name to it.label }
    }

    private val groups = listOf(
        "Navigation" to listOf(NavAction.HOME, NavAction.BACK, NavAction.RECENTS, NavAction.NOTIFICATIONS,
            NavAction.QUICK_SETTINGS, NavAction.LOCK, NavAction.POWER, NavAction.SCREENSHOT),
        "Sound & display" to listOf(NavAction.VOLUME_UP, NavAction.VOLUME_DOWN, NavAction.MIXER, NavAction.SOUND_MODE,
            NavAction.BRIGHTNESS, NavAction.TIMEOUT, NavAction.ROTATION),
        "Connectivity" to listOf(NavAction.WIFI, NavAction.DATA, NavAction.BLUETOOTH, NavAction.LOCATION, NavAction.AIRPLANE),
        "Apps & tools" to listOf(NavAction.SEARCH, NavAction.AI, NavAction.CAMERA, NavAction.SELFIE, NavAction.GALLERY,
            NavAction.TORCH, NavAction.RECORDER, NavAction.STORAGE, NavAction.SETTINGS, NavAction.FAVORITE, NavAction.HIDE)
    )

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.isNavigationBarContrastEnforced = false

        val rootFrame = FrameLayout(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL

        appBar = buildAppBar()
        content.addView(appBar, lp(MATCH, WRAP))
        scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.isVerticalScrollBarEnabled = false
        content.addView(scroll, lp(MATCH, 0).also { it.weight = 1f })
        navBar = buildNavBar()
        content.addView(navBar, lp(MATCH, WRAP))
        rootFrame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        drawerWidth = minOf(dp(310), (resources.displayMetrics.widthPixels * 0.84f).toInt())
        drawerScrim = FrameLayout(this)
        drawerScrim.setBackgroundColor(0x99050813.toInt())
        drawerScrim.visibility = View.GONE
        drawerScrim.setOnClickListener { closeDrawer() }
        rootFrame.addView(drawerScrim, FrameLayout.LayoutParams(MATCH, MATCH))
        drawerPanel = buildDrawer()
        drawerPanel.translationX = -drawerWidth.toFloat()
        rootFrame.addView(drawerPanel, FrameLayout.LayoutParams(drawerWidth, MATCH, Gravity.START))

        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars())
            appBar.setPadding(b.left + dp(6), b.top + dp(6), b.right + dp(10), dp(6))
            navBar.setPadding(b.left, 0, b.right, b.bottom)
            drawerPanel.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        setContentView(rootFrame)

        requestRuntimePermissions()
        if (savedInstanceState == null && intent.getBooleanExtra("open_ai", false)) {
            page = 6
            handler.post { showProviderDialog() }
        }
    }

    override fun onResume() {
        super.onResume()
        scenes.values.forEach { it.onResume() }
        refresh(true)
        handler.postDelayed(ticker, 1200)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        scenes.values.forEach { it.onPause() }
        super.onPause()
    }

    override fun onDestroy() {
        scenes.values.forEach {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        scenes.clear()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawerOpen -> closeDrawer()
            page != 0 -> navigate(0)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 101 && Cleaner.hasMediaAccess(this)) scan()
        refresh(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77) {
            if (resultCode == RESULT_OK) toast("Deleted")
            picked.clear()
            scan()
            rebuild()
        }
    }

    // ------------------------------------------------------------------ state

    private fun hasNotif() =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasDnd() =
        getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    /** All runtime permissions, requested together. */
    private fun requestRuntimePermissions() {
        val missing = arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    private fun signature(): String = listOf(
        hasNotif(), hasMic(), hasDnd(), Settings.System.canWrite(this), Settings.canDrawOverlays(this),
        Access.isEnabled(this), Access.isRunning(), OverlayService.running, RecordService.recording,
        AiConfig.load(this).ready, Access.canWriteSecure(this)
    ).joinToString("|")

    private fun refresh(force: Boolean) {
        val sig = signature()
        statusChip.text = if (OverlayService.running) "● Active" else "● Stopped"
        statusChip.setTextColor(if (OverlayService.running) Ui.GREEN else Ui.MUTED)
        if (!force && sig == lastSig) return
        lastSig = sig
        rebuild()
    }

    private fun rebuild() {
        val y = scroll.scrollY
        scroll.removeAllViews()
        scroll.addView(buildPage())
        scroll.post { scroll.scrollTo(0, y) }
        updateChrome()
    }

    private fun refreshPreview() {
        val h = previewHolder ?: return
        val b = previewBuilder ?: return
        h.removeAllViews()
        h.addView(b())
    }

    private fun navigate(id: Int, subIndex: Int = -1) {
        page = id
        if (subIndex >= 0) sub[id] = subIndex
        previewHolder = null
        lastSig = signature()
        scroll.removeAllViews()
        scroll.addView(buildPage())
        scroll.scrollTo(0, 0)
        updateChrome()
        closeDrawer()
    }

    // ------------------------------------------------------------------ chrome: app bar, bottom nav, drawer

    private fun buildAppBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val menu = ImageView(this)
        menu.setImageResource(R.drawable.ic_menu)
        menu.setPadding(dp(12), dp(12), dp(12), dp(12))
        menu.background = Ui.ripple(ColorDrawable(0), dp(22).toFloat())
        menu.contentDescription = "Open menu"
        menu.setOnClickListener { openDrawer() }
        bar.addView(menu, lp(dp(48), dp(48)))
        titleView = Ui.text(this, "Home", 20f, Ui.TEXT, weight = 700)
        titleView.setPadding(dp(6), 0, 0, 0)
        bar.addView(titleView, LinearLayout.LayoutParams(0, WRAP, 1f))
        statusChip = Ui.text(this, "● Stopped", 11.5f, Ui.MUTED, weight = 600)
        statusChip.setPadding(dp(10), dp(5), dp(10), dp(5))
        statusChip.background = Ui.card3d(this, 12)
        statusChip.setOnClickListener { toggleOverlay() }
        bar.addView(statusChip)
        return bar
    }

    private fun buildNavBar(): LinearLayout {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setBackgroundColor(0xF20C1220.toInt())
        val line = View(this)
        line.setBackgroundColor(Ui.BORDER)
        wrap.addView(line, lp(MATCH, 1))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(6), dp(8), dp(6), dp(8))
        val items = listOf(Triple("Home", R.drawable.ic_home, 0), Triple("Menu", R.drawable.ic_apps, 1),
            Triple("Design", R.drawable.ic_palette, 2), Triple("Record", R.drawable.ic_record, 3),
            Triple("More", R.drawable.ic_more, -1))
        items.forEach { (name, icon, target) ->
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            val pill = FrameLayout(this)
            val iv = ImageView(this)
            iv.setImageResource(icon)
            pill.addView(iv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            item.addView(pill, lp(dp(56), dp(32)))
            val label = Ui.text(this, name, 10.5f, Ui.MUTED, weight = 600)
            label.setPadding(0, dp(3), 0, 0)
            item.addView(label)
            item.setOnClickListener { if (target < 0) openDrawer() else navigate(target) }
            navPills.add(pill)
            navIcons.add(iv)
            navLabels.add(label)
            row.addView(item, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        wrap.addView(row, lp(MATCH, WRAP))
        return wrap
    }

    private fun updateChrome() {
        titleView.text = pages[page].title
        val navTargets = listOf(0, 1, 2, 3, -1)
        for (i in navTargets.indices) {
            val sel = navTargets[i] == page
            navPills[i].background = if (sel) Ui.button3d(this, Ui.VIOLET, 16) else null
            if (sel) Ui.lift(navPills[i], this, Ui.VIOLET, 6) else navPills[i].elevation = 0f
            navIcons[i].alpha = if (sel) 1f else 0.55f
            navLabels[i].setTextColor(if (sel) Ui.TEXT else Ui.MUTED)
        }
        for ((id, row) in drawerRows) {
            val sel = id == page
            row.background = if (sel) Ui.shape(0x336366F1, dp(14).toFloat(), 0x666366F1, dp(1)) else Ui.ripple(ColorDrawable(0), dp(14).toFloat())
        }
    }

    private fun buildDrawer(): ScrollView {
        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF0C1220.toInt())
        sv.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(18), dp(14), dp(20))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(4), 0, 0, dp(18))
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.orb3d_ring)
        head.addView(logo, lp(dp(54), dp(54)))
        val names = LinearLayout(this)
        names.orientation = LinearLayout.VERTICAL
        names.setPadding(dp(12), 0, 0, 0)
        names.addView(Ui.text(this, "NavAssist", 20f, Ui.TEXT, weight = 700))
        names.addView(Ui.text(this, "Version 4.0", 12f, Ui.MUTED))
        head.addView(names)
        col.addView(head)

        pages.forEach { p ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(12), dp(12), dp(12))
            val iv = ImageView(this)
            iv.setImageResource(p.pic)
            row.addView(iv, lp(dp(38), dp(38)).also { it.marginEnd = dp(14) })
            row.addView(Ui.text(this, p.title, 15f, Ui.TEXT, weight = 600))
            row.setOnClickListener { navigate(p.id) }
            drawerRows.add(p.id to row)
            col.addView(row, lp(MATCH, WRAP).also { it.bottomMargin = dp(2) })
        }
        sv.addView(col)
        return sv
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawerScrim.visibility = View.VISIBLE
        drawerScrim.alpha = 0f
        drawerScrim.animate().alpha(1f).setDuration(180).start()
        drawerPanel.animate().translationX(0f).setDuration(220).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawerScrim.animate().alpha(0f).setDuration(160).withEndAction { drawerScrim.visibility = View.GONE }.start()
        drawerPanel.animate().translationX(-drawerWidth.toFloat()).setDuration(200).start()
    }

    // ------------------------------------------------------------------ page switch

    private fun buildPage(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(6), dp(18), dp(28))
        previewHolder = null
        previewBuilder = null
        when (page) {
            0 -> homePage(col)
            1 -> menuPage(col)
            2 -> designPage(col)
            3 -> recordingPage(col)
            4 -> cleaningPage(col)
            5 -> connectionsPage(col)
            6 -> aiPage(col)
            7 -> featuresPage(col)
            8 -> settingsPage(col)
            else -> aboutPage(col)
        }
        return col
    }

    private fun subTabBar(col: LinearLayout) {
        val items = pages[page].subs
        if (items.size < 2) return
        col.addView(chips(items, sub[page]) {
            sub[page] = it
            previewHolder = null
            rebuild()
            scroll.scrollTo(0, 0)
        }, lp(MATCH, WRAP).also { it.bottomMargin = dp(14) })
    }

    // ---------------------------------------------------------------- 3D scene (Three.js in a WebView)

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun scene3d(name: String, heightDp: Int, fallback: Int, shift: Int): View {
        val box = FrameLayout(this)
        box.background = Ui.card3d(this, 22)
        box.clipToOutline = true
        val img = ImageView(this)
        img.setImageResource(fallback)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        box.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
        val key = "$name$shift"
        var wv = scenes[key]
        if (wv == null) {
            wv = WebView(this)
            wv.setBackgroundColor(Color.TRANSPARENT)
            wv.overScrollMode = View.OVER_SCROLL_NEVER
            wv.isVerticalScrollBarEnabled = false
            wv.isHorizontalScrollBarEnabled = false
            wv.settings.javaScriptEnabled = true
            wv.settings.allowFileAccess = true
            wv.settings.allowContentAccess = false
            var downX = 0f
            var downY = 0f
            wv.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                    MotionEvent.ACTION_MOVE ->
                        v.parent?.requestDisallowInterceptTouchEvent(Math.abs(e.x - downX) > Math.abs(e.y - downY))
                }
                false
            }
            wv.loadUrl("file:///android_asset/hero3d.html?scene=$name&shift=$shift")
            scenes[key] = wv
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
        box.addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))
        box.layoutParams = lp(MATCH, dp(heightDp))
        box.elevation = dp(4).toFloat()
        return box
    }

    /** 3D scene with a title and subtitle written over its left half. */
    private fun sceneBanner(name: String, title: String, sub: String, heightDp: Int, fallback: Int): View {
        val frame = FrameLayout(this)
        frame.addView(scene3d(name, heightDp, fallback, 1), FrameLayout.LayoutParams(MATCH, MATCH))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.gravity = Gravity.CENTER_VERTICAL
        txt.setPadding(dp(18), dp(10), dp(8), dp(10))
        txt.addView(Ui.text(this, title, 22f, Ui.TEXT, weight = 700))
        val s = Ui.text(this, sub, 12f, Ui.MUTED)
        s.setPadding(0, dp(6), 0, 0)
        txt.addView(s)
        txt.isClickable = false
        frame.addView(txt, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.46f).toInt(), MATCH))
        frame.layoutParams = lp(MATCH, dp(heightDp))
        return frame
    }

    // ---------------------------------------------------------------- HOME

    private fun homePage(col: LinearLayout) {
        val running = OverlayService.running
        val overlay = Settings.canDrawOverlays(this)
        val a11y = Access.isEnabled(this)

        col.addView(sceneBanner("orb", "Your buttons,\nright on screen.", "Home · Back · Recents\nPower · Volume · AI", 230, R.drawable.hero_3d))

        col.addView(bigButton(if (running) "Stop floating assistant" else "Start floating assistant",
            if (running) R.drawable.ic_stop else R.drawable.ic_play, if (running) Ui.RED else Ui.VIOLET) { toggleOverlay() },
            lp(MATCH, dp(56)).also { it.topMargin = dp(14); it.bottomMargin = dp(6) })

        val essentials = listOf(overlay, a11y, hasNotif() && hasMic())
        col.addView(label("QUICK SETUP  ·  ${essentials.count { it }} of 3 ready"))
        val setup = card()
        setup.addView(stepRow(1, "Display over other apps", "Draws the floating orb", overlay, "Open") {
            safeOpen(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        setup.addView(divider())
        setup.addView(stepRow(2, "Accessibility service", "Back, Recents, Lock, Power",
            a11y, if (Access.canWriteSecure(this)) "Enable now" else "Enable") { openAccessibility() })
        setup.addView(divider())
        setup.addView(stepRow(3, "Permissions", "Notifications and microphone", hasNotif() && hasMic(), "Allow") { requestRuntimePermissions() })
        col.addView(setup)
        if (!a11y) col.addView(restrictedCard())

        col.addView(label("EXPLORE"))
        val explore = listOf(
            Triple("Menu", pages[1].pic, 1), Triple("Design", pages[2].pic, 2),
            Triple("Recording", pages[3].pic, 3), Triple("Cleaning", pages[4].pic, 4),
            Triple("Connections", pages[5].pic, 5), Triple("AI chat", pages[6].pic, 6),
            Triple("Features", pages[7].pic, 7), Triple("Settings", pages[8].pic, 8),
            Triple("About us", pages[9].pic, 9)
        )
        for (rowItems in explore.chunked(3)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for ((name, icon, id) in rowItems) {
                r.addView(miniTilePic(icon, name) { navigate(id) }, LinearLayout.LayoutParams(0, WRAP, 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            col.addView(r)
        }

        col.addView(label("QUICK ACTIONS"))
        val actions = listOf(
            Triple("Hide orb", R.drawable.ic_visibility_off) { sendOverlay(OverlayService.ACTION_TOGGLE) },
            Triple("Screenshot", R.drawable.ic_crop) { screenshotSoon() },
            Triple("Record", R.drawable.ic_record) { startActivity(Intent(this, RecordActivity::class.java)) },
            Triple("AI chat", R.drawable.ic_sparkle) { startActivity(Intent(this, AiChatActivity::class.java)) },
            Triple("Search", R.drawable.ic_search) { startActivity(Intent(this, SearchActivity::class.java)) },
            Triple("Test Recents", R.drawable.ic_recents) { testRecents() }
        )
        for (rowItems in actions.chunked(3)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for ((name, icon, act) in rowItems) {
                r.addView(miniTile(icon, name, act), LinearLayout.LayoutParams(0, WRAP, 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            col.addView(r)
        }

        col.addView(label("TIP"))
        val tip = card()
        tip.setPadding(dp(16), dp(14), dp(16), dp(14))
        tip.addView(Ui.text(this, "Taking a screenshot yourself?", 15f, Ui.TEXT, weight = 600))
        val tx = Ui.text(this, "Long-press the orb to hide it for a few seconds, or add the NavAssist tile to Quick Settings. The Screenshot button in the menu hides the orb for you.", 12.5f, Ui.MUTED)
        tx.setPadding(0, dp(6), 0, 0)
        tip.addView(tx)
        col.addView(tip)
    }

    // ---------------------------------------------------------------- MENU & GESTURES

    private fun menuPage(col: LinearLayout) {
        col.addView(banner(R.drawable.banner_menu, "Your menu,\nyour way.", "Pick buttons, layout and gestures", 140))
        col.addView(spacer(12))
        subTabBar(col)
        when (sub[1]) {
            0 -> menuLayout(col)
            1 -> menuButtons(col)
            2 -> menuGestures(col)
            else -> menuFavorites(col)
        }
    }

    private fun menuLayout(col: LinearLayout) {
        col.addView(label("LIVE PREVIEW"))
        val holder = FrameLayout(this)
        previewHolder = holder
        previewBuilder = { menuPreview() }
        holder.addView(menuPreview())
        col.addView(holder, lp(MATCH, WRAP).also { it.bottomMargin = dp(16) })

        col.addView(label("SELECT A LAYOUT"))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val specs = listOf(Triple(3, 3, "3 × 3"), Triple(3, 4, "3 × 4"), Triple(4, 4, "4 × 4"))
        specs.forEachIndexed { i, (cols, rows, name) ->
            val sel = Prefs.layout(this) == i
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.setPadding(dp(8), dp(12), dp(8), dp(10))
            c.background = Ui.card3d(this, 16, if (sel) Ui.VIOLET else 0)
            if (sel) Ui.lift(c, this, Ui.VIOLET, 6)
            val mini = LinearLayout(this)
            mini.orientation = LinearLayout.VERTICAL
            for (r in 0 until rows) {
                val mr = LinearLayout(this)
                for (k in 0 until cols) {
                    val sq = View(this)
                    sq.background = Ui.shape(if (sel) Ui.VIOLET else 0x55FFFFFF, dp(3).toFloat())
                    mr.addView(sq, lp(dp(12), dp(12)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                mini.addView(mr)
            }
            c.addView(mini)
            val t = Ui.text(this, name, 13f, if (sel) Ui.TEXT else Ui.MUTED, weight = 600)
            t.setPadding(0, dp(8), 0, 0)
            c.addView(t)
            c.setOnClickListener { Prefs.setLayout(this, i); rebuild() }
            row.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        col.addView(row)
        col.addView(spacer(12))
        val c2 = card()
        c2.addView(switchRow("Icon tiles", "Rounded backgrounds behind menu icons", Prefs.iconTiles(this)) {
            Prefs.setIconTiles(this, it)
            refreshPreview()
        })
        c2.addView(divider())
        c2.addView(sliderRow("Menu transparency (higher = solid)", 50, 100, Prefs.panelOpacity(this), "%") {
            Prefs.setPanelOpacity(this, it)
            refreshPreview()
        })
        col.addView(c2)
        val n = Prefs.enabledActions(this).size
        val per = Prefs.menuCols(this) * Prefs.menuRows(this)
        col.addView(Ui.text(this, "$n buttons on ${(n + per - 1) / per} page(s). Swipe the menu sideways to change page.", 12f, Ui.MUTED))
    }

    private fun menuButtons(col: LinearLayout) {
        val cur = Prefs.enabledActions(this).toSet()
        col.addView(Ui.text(this, "${cur.size} of ${NavAction.values().size} buttons shown. Switch any button on or off.", 12.5f, Ui.MUTED),
            lp(MATCH, WRAP).also { it.bottomMargin = dp(10) })
        for ((name, list) in groups) {
            col.addView(label(name.uppercase()))
            val c = card()
            list.forEachIndexed { idx, a ->
                if (idx > 0) c.addView(divider())
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(9), 0, dp(9))
                val ic = ImageView(this)
                ic.setImageResource(a.icon)
                ic.setPadding(dp(9), dp(9), dp(9), dp(9))
                ic.background = Ui.shape(0x1FFFFFFF, dp(12).toFloat(), 0x26FFFFFF, dp(1))
                row.addView(ic, lp(dp(40), dp(40)).also { it.marginEnd = dp(12) })
                row.addView(Ui.text(this, a.label, 15f, Ui.TEXT, weight = 500), LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(styledSwitch(a in cur) { on -> toggleAction(a, on) })
                c.addView(row)
            }
            col.addView(c)
        }
    }

    private fun toggleAction(a: NavAction, on: Boolean) {
        val list = Prefs.enabledActions(this).toMutableList()
        if (on) {
            if (a !in list) list.add(a)
        } else {
            if (list.size <= 1) {
                toast("Keep at least one button")
                rebuild()
                return
            }
            list.remove(a)
        }
        list.sortBy { it.ordinal }
        Prefs.setEnabledActions(this, list)
    }

    private fun gestureGet(i: Int) = when (i) {
        0 -> Prefs.tapAction(this)
        1 -> Prefs.doubleAction(this)
        else -> Prefs.longAction(this)
    }

    private fun gestureSet(i: Int, v: String) {
        when (i) {
            0 -> Prefs.setTapAction(this, v)
            1 -> Prefs.setDoubleAction(this, v)
            else -> Prefs.setLongAction(this, v)
        }
    }

    private fun gestureLabel(code: String) = gestureOptions.firstOrNull { it.first == code }?.second ?: code

    private fun menuGestures(col: LinearLayout) {
        col.addView(label("TAP THE ORB TO..."))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        listOf("Single tap", "Double tap", "Long press").forEachIndexed { i, name ->
            val sel = gestureSel == i
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.setPadding(dp(6), dp(14), dp(6), dp(12))
            c.background = Ui.card3d(this, 16, if (sel) Ui.VIOLET else 0)
            if (sel) Ui.lift(c, this, Ui.VIOLET, 6)
            val ic = ImageView(this)
            ic.setImageResource(R.drawable.ic_touch)
            ic.setPadding(dp(10), dp(10), dp(10), dp(10))
            ic.background = if (sel) Ui.button3d(this, Ui.VIOLET, 14) else Ui.shape(0x1FFFFFFF, dp(14).toFloat(), 0x26FFFFFF, dp(1))
            c.addView(ic, lp(dp(46), dp(46)))
            val t = Ui.text(this, name, 13f, Ui.TEXT, weight = 600)
            t.setPadding(0, dp(8), 0, 0)
            c.addView(t)
            val v = Ui.text(this, gestureLabel(gestureGet(i)), 11.5f, Ui.MUTED)
            v.maxLines = 1
            c.addView(v)
            c.setOnClickListener { gestureSel = i; rebuild() }
            row.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        col.addView(row)
        col.addView(label("CHOOSE ACTION FOR ${listOf("SINGLE TAP", "DOUBLE TAP", "LONG PRESS")[gestureSel]}"))
        val c = card()
        val cur = gestureGet(gestureSel)
        gestureOptions.forEachIndexed { idx, (code, name) ->
            if (idx > 0) c.addView(divider())
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(0, dp(13), 0, dp(13))
            r.background = Ui.ripple(ColorDrawable(0), dp(10).toFloat())
            r.addView(Ui.text(this, name, 15f, if (code == cur) Ui.TEXT else 0xFFD1D5DB.toInt(), weight = if (code == cur) 600 else 400),
                LinearLayout.LayoutParams(0, WRAP, 1f))
            if (code == cur) r.addView(Ui.text(this, "✓", 18f, Ui.CYAN, weight = 700))
            r.setOnClickListener { gestureSet(gestureSel, code); rebuild() }
            c.addView(r)
        }
        col.addView(c)
        col.addView(label("“HIDE ORB” LASTS"))
        col.addView(chips(hideOptions.map { it.second }, hideOptions.indexOfFirst { it.first == Prefs.hideSeconds(this) }) {
            Prefs.setHideSeconds(this, hideOptions[it].first)
            rebuild()
        })
    }

    private fun menuFavorites(col: LinearLayout) {
        val favs = Prefs.favorites(this)
        col.addView(Ui.text(this, "Shown when you tap the Favorites button in the menu. Up to 12 apps.", 12.5f, Ui.MUTED),
            lp(MATCH, WRAP).also { it.bottomMargin = dp(10) })
        val c = card()
        c.setPadding(dp(10), dp(12), dp(10), dp(12))
        if (favs.isEmpty()) {
            val e = Ui.text(this, "No favorite apps yet.", 14f, Ui.MUTED)
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(18), 0, dp(18))
            c.addView(e, lp(MATCH, WRAP))
        } else {
            for (rowApps in favs.take(12).chunked(4)) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                for (pkg in rowApps) {
                    val cell = LinearLayout(this)
                    cell.orientation = LinearLayout.VERTICAL
                    cell.gravity = Gravity.CENTER_HORIZONTAL
                    cell.setPadding(0, dp(6), 0, dp(6))
                    val iv = ImageView(this)
                    try {
                        iv.setImageDrawable(packageManager.getApplicationIcon(pkg))
                    } catch (e: Exception) {
                        iv.setImageResource(R.drawable.ic_apps)
                    }
                    cell.addView(iv, lp(dp(44), dp(44)))
                    val l = Ui.text(this, Apps.label(this, pkg) ?: pkg, 10.5f, Ui.MUTED)
                    l.maxLines = 1
                    l.gravity = Gravity.CENTER
                    cell.addView(l, lp(MATCH, WRAP).also { it.topMargin = dp(4) })
                    r.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
                }
                repeat(4 - rowApps.size) { r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
                c.addView(r)
            }
        }
        col.addView(c)
        col.addView(bigButton("Choose favorite apps", R.drawable.ic_star, Ui.VIOLET) { pickFavorites() },
            lp(MATCH, dp(52)).also { it.topMargin = dp(6) })
    }

    // ---------------------------------------------------------------- DESIGN

    private fun designPage(col: LinearLayout) {
        col.addView(banner(R.drawable.banner_style, "Make it\nyours.", "Themes, colors, orb and motion", 140))
        col.addView(spacer(12))
        subTabBar(col)
        when (sub[2]) {
            0 -> designTheme(col)
            1 -> designOrb(col)
            else -> designMotion(col)
        }
    }

    private fun designTheme(col: LinearLayout) {
        col.addView(label("LIVE PREVIEW"))
        val holder = FrameLayout(this)
        previewHolder = holder
        previewBuilder = { menuPreview() }
        holder.addView(menuPreview())
        col.addView(holder, lp(MATCH, WRAP).also { it.bottomMargin = dp(16) })

        col.addView(label("MENU THEME"))
        for (pair in Themes.all.withIndex().chunked(2)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for ((i, th) in pair) {
                val sel = Prefs.theme(this) == i
                val c = LinearLayout(this)
                c.orientation = LinearLayout.VERTICAL
                c.setPadding(dp(8), dp(8), dp(8), dp(10))
                c.background = Ui.card3d(this, 16, if (sel) Themes.accent(this) else 0)
                if (sel) Ui.lift(c, this, Themes.accent(this), 6)
                val sw = View(this)
                sw.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(th.top, th.bottom)).apply {
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), th.stroke)
                }
                c.addView(sw, lp(MATCH, dp(54)))
                val t = Ui.text(this, if (sel) "✓  ${th.name}" else th.name, 13f, Ui.TEXT, weight = 600)
                t.setPadding(dp(2), dp(8), 0, 0)
                c.addView(t)
                c.setOnClickListener { Prefs.setTheme(this, i); rebuild() }
                r.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            if (pair.size == 1) r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            col.addView(r)
        }

        col.addView(label("ACCENT COLOR"))
        val c = card()
        c.setPadding(dp(10), dp(12), dp(10), dp(12))
        for (rowIdx in Themes.accents.indices.chunked(6)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (i in rowIdx) {
                val sel = Prefs.accent(this) == i
                val col0 = Themes.accents[i]
                val dot = Ui.text(this, if (sel) "✓" else "", 17f, if (i == Themes.accents.size - 1) Color.BLACK else Color.WHITE, weight = 700)
                dot.gravity = Gravity.CENTER
                val g = GradientDrawable()
                g.shape = GradientDrawable.OVAL
                g.gradientType = GradientDrawable.RADIAL_GRADIENT
                g.gradientRadius = dp(26).toFloat()
                g.setGradientCenter(0.35f, 0.3f)
                g.colors = intArrayOf(Ui.lighten(col0, 0.55f), col0, Ui.darken(col0, 0.35f))
                if (sel) g.setStroke(dp(2), Color.WHITE)
                dot.background = g
                if (sel) Ui.lift(dot, this, col0, 6)
                dot.setOnClickListener { Prefs.setAccent(this, i); rebuild() }
                r.addView(dot, LinearLayout.LayoutParams(0, dp(42), 1f).also { it.setMargins(dp(5), dp(6), dp(5), dp(6)) })
            }
            c.addView(r)
        }
        col.addView(c)
    }

    private fun orbPreview(): View {
        val stage = FrameLayout(this)
        val bg = ImageView(this)
        bg.setImageResource(R.drawable.bg_aurora)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        bg.background = Ui.card3d(this, 20)
        bg.clipToOutline = true
        stage.addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))
        val size = dp(56) * Prefs.orbScale(this) / 100
        val orb = Ui.orbView(this, size)
        orb.alpha = Prefs.opacity(this) / 100f
        stage.addView(orb, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val cap = Ui.text(this, "Preview", 11f, Ui.MUTED)
        stage.addView(cap, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).also { it.setMargins(dp(14), 0, 0, dp(10)) })
        stage.layoutParams = lp(MATCH, dp(130))
        return stage
    }

    private fun designOrb(col: LinearLayout) {
        col.addView(label("LIVE PREVIEW"))
        val holder = FrameLayout(this)
        previewHolder = holder
        previewBuilder = { orbPreview() }
        holder.addView(orbPreview())
        col.addView(holder, lp(MATCH, WRAP).also { it.bottomMargin = dp(16) })

        col.addView(label("ORB ICON"))
        val c = card()
        c.setPadding(dp(8), dp(10), dp(8), dp(10))
        val cur = Prefs.orbIcon(this)
        val cells = ArrayList<Pair<String, View>>()
        for ((key, _) in OrbIcons.pictures) {
            val iv = ImageView(this)
            iv.setImageResource(OrbIcons.res(key))
            iv.setPadding(dp(4), dp(4), dp(4), dp(4))
            cells.add(key to iv)
        }
        for ((key, res) in OrbIcons.symbols) {
            val iv = ImageView(this)
            iv.setImageResource(res)
            iv.setPadding(dp(14), dp(14), dp(14), dp(14))
            cells.add(key to iv)
        }
        for (e in OrbIcons.emojis) {
            val t = TextView(this)
            t.text = e
            t.textSize = 24f
            t.gravity = Gravity.CENTER
            cells.add("emoji:$e" to t)
        }
        for (rowCells in cells.chunked(5)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for ((key, v) in rowCells) {
                val sel = key == cur
                v.background = Ui.shape(0x22FFFFFF, dp(14).toFloat(), if (sel) Ui.VIOLET else 0x33FFFFFF, dp(if (sel) 2 else 1))
                v.setOnClickListener { Prefs.setOrbIcon(this, key); rebuild() }
                r.addView(v, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            repeat(5 - rowCells.size) { r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            c.addView(r)
        }
        col.addView(c)
        col.addView(spacer(12))
        val c2 = card()
        c2.addView(switchRow("Rounded-square orb", "Off = circle (not used by 3D pictures)", Prefs.orbShape(this) == 1) {
            Prefs.setOrbShape(this, if (it) 1 else 0)
            refreshPreview()
        })
        c2.addView(divider())
        c2.addView(sliderRow("Orb size", 60, 140, Prefs.orbScale(this), "%") { Prefs.setOrbScale(this, it); refreshPreview() })
        c2.addView(divider())
        c2.addView(sliderRow("Orb opacity when idle", 20, 100, Prefs.opacity(this), "%") { Prefs.setOpacity(this, it); refreshPreview() })
        col.addView(c2)
    }

    private fun designMotion(col: LinearLayout) {
        val c = card()
        c.addView(sliderRow("Animation speed", 0, 100, Prefs.animSpeed(this), "%") { Prefs.setAnimSpeed(this, it) })
        c.addView(divider())
        c.addView(switchRow("Peek at screen edge", "Idle orb slides half off-screen", Prefs.peek(this)) { Prefs.setPeek(this, it) })
        c.addView(divider())
        c.addView(switchRow("Snap to screen edge", "The orb glides to the nearest side", Prefs.snapToEdge(this)) { Prefs.setSnapToEdge(this, it) })
        c.addView(divider())
        c.addView(switchRow("Haptic feedback", "Short vibration on taps", Prefs.haptics(this)) { Prefs.setHaptics(this, it) })
        col.addView(c)
    }

    /** Mini version of the real floating menu, using the current theme, layout and buttons. */
    private fun menuPreview(): View {
        val stage = FrameLayout(this)
        stage.background = Ui.card3d(this, 20)
        stage.setPadding(dp(16), dp(16), dp(16), dp(16))
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = Ui.menuPanelBackground(this)
        panel.setPadding(dp(10), dp(8), dp(10), dp(10))
        val cols = Prefs.menuCols(this)
        val rows = Prefs.menuRows(this)
        val all = Prefs.enabledActions(this)
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val lg = ImageView(this)
        lg.setImageResource(R.drawable.ic_orb)
        head.addView(lg, lp(dp(16), dp(16)))
        val tt = Ui.text(this, "NavAssist", 11.5f, Ui.TEXT, weight = 600)
        tt.setPadding(dp(6), 0, 0, 0)
        head.addView(tt)
        panel.addView(head)
        for (rowActs in all.take(cols * rows).chunked(cols)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (a in rowActs) {
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                cell.setPadding(0, dp(5), 0, dp(2))
                val iv = ImageView(this)
                iv.setImageResource(a.icon)
                iv.setPadding(dp(9), dp(9), dp(9), dp(9))
                iv.background = Ui.menuChipBg(this, a == NavAction.AI)
                cell.addView(iv, lp(dp(40), dp(40)))
                val l = Ui.text(this, a.label, 9f, 0xCCFFFFFF.toInt(), weight = 500)
                l.maxLines = 1
                l.setPadding(0, dp(2), 0, 0)
                cell.addView(l)
                r.addView(cell, lp(dp(58), WRAP))
            }
            repeat(cols - rowActs.size) { r.addView(View(this), lp(dp(58), 1)) }
            panel.addView(r)
        }
        val pages = (all.size + cols * rows - 1) / (cols * rows)
        if (pages > 1) {
            val dots = LinearLayout(this)
            dots.gravity = Gravity.CENTER
            for (p in 0 until pages) {
                val d = View(this)
                d.background = Ui.oval(if (p == 0) Themes.accent(this) else 0x44FFFFFF)
                dots.addView(d, lp(dp(6), dp(6)).also { it.setMargins(dp(3), dp(6), dp(3), 0) })
            }
            panel.addView(dots, lp(MATCH, WRAP))
        }
        stage.addView(panel, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        return stage
    }

    // ---------------------------------------------------------------- RECORDING

    private fun recordingPage(col: LinearLayout) {
        col.addView(banner(R.drawable.banner_tools, "Record your\nscreen.", "HD video with optional sound", 140))
        col.addView(spacer(12))
        subTabBar(col)
        if (sub[3] == 0) recorderSettings(col) else recorderLibrary(col)
    }

    private fun settingChips(col: LinearLayout, title: String, items: List<String>, sel: Int, onPick: (Int) -> Unit) {
        col.addView(label(title))
        col.addView(chips(items, sel) { onPick(it); rebuild() }, lp(MATCH, WRAP).also { it.bottomMargin = dp(6) })
    }

    private fun recorderSettings(col: LinearLayout) {
        val rec = RecordService.recording
        col.addView(bigButton(if (rec) "Stop recording" else "Start recording",
            if (rec) R.drawable.ic_stop else R.drawable.ic_record, Ui.RED) {
            startActivity(Intent(this, RecordActivity::class.java))
        }, lp(MATCH, dp(56)).also { it.bottomMargin = dp(6) })
        col.addView(Ui.text(this, "Videos are saved in Movies/NavAssist. Android asks for permission every time you start.", 12f, Ui.MUTED))
        settingChips(col, "RESOLUTION", resolutions, Prefs.recRes(this)) { Prefs.setRecRes(this, it) }
        settingChips(col, "BITRATE", bitrates, Prefs.recBitrate(this)) { Prefs.setRecBitrate(this, it) }
        settingChips(col, "FRAME RATE", fpsList, Prefs.recFps(this)) { Prefs.setRecFps(this, it) }
        settingChips(col, "SOUND", sounds, Prefs.recSound(this)) { Prefs.setRecSound(this, it) }
        col.addView(spacer(8))
        val c = card()
        c.addView(switchRow("Hide orb while recording", "Keeps it out of your video", Prefs.recHideOrb(this)) { Prefs.setRecHideOrb(this, it) })
        col.addView(c)
    }

    private fun emptyState(text: String): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(0, dp(8), 0, dp(8))
        val img = ImageView(this)
        img.setImageResource(R.drawable.empty_3d)
        box.addView(img, lp(dp(220), dp(138)))
        val t = Ui.text(this, text, 13.5f, Ui.MUTED)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(4), dp(16), dp(8))
        box.addView(t)
        return box
    }

    private fun recorderLibrary(col: LinearLayout) {
        val list = Cleaner.recordings(this)
        if (list.isEmpty()) {
            col.addView(emptyState("No recordings yet.\nStart one from the Record tab, the floating menu or the Home page."))
            col.addView(bigButton("Start recording", R.drawable.ic_record, Ui.RED) {
                startActivity(Intent(this, RecordActivity::class.java))
            }, lp(MATCH, dp(52)))
            return
        }
        col.addView(Ui.text(this, "${list.size} recording(s) · ${Cleaner.fmt(list.sumOf { it.size })}", 12.5f, Ui.MUTED),
            lp(MATCH, WRAP).also { it.bottomMargin = dp(10) })
        for (m in list.take(40)) col.addView(recordingRow(m))
    }

    private fun recordingRow(m: Cleaner.Media): View {
        val c = card()
        c.setPadding(dp(12), dp(12), dp(12), dp(12))
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val thumb = ImageView(this)
        thumb.scaleType = ImageView.ScaleType.CENTER_CROP
        thumb.background = Ui.shape(0xFF0B0F19.toInt(), dp(10).toFloat())
        thumb.clipToOutline = true
        try {
            thumb.setImageBitmap(contentResolver.loadThumbnail(m.uri, Size(240, 144), null))
        } catch (e: Exception) {
            thumb.setImageResource(R.drawable.i3d_record)
        }
        top.addView(thumb, lp(dp(104), dp(62)).also { it.marginEnd = dp(12) })
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        val name = Ui.text(this, m.name, 13.5f, Ui.TEXT, weight = 600)
        name.maxLines = 1
        t.addView(name)
        val secs = m.durationMs / 1000
        t.addView(Ui.text(this, "${Cleaner.fmt(m.size)} · %d:%02d · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(m.dateMs))}".format(secs / 60, secs % 60), 11.5f, Ui.MUTED))
        top.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        c.addView(top)
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setPadding(0, dp(10), 0, 0)
        fun act(label: String, icon: Int, color: Int, onClick: () -> Unit) {
            val b = LinearLayout(this)
            b.orientation = LinearLayout.HORIZONTAL
            b.gravity = Gravity.CENTER
            b.background = Ui.button3d(this, color, 10)
            val iv = ImageView(this)
            iv.setImageResource(icon)
            b.addView(iv, lp(dp(16), dp(16)).also { it.marginEnd = dp(6) })
            b.addView(Ui.text(this, label, 12.5f, Color.WHITE, weight = 600))
            b.setPadding(0, dp(9), 0, dp(9))
            b.setOnClickListener { onClick() }
            actions.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).also { it.setMargins(dp(3), 0, dp(3), 0) })
        }
        act("Play", R.drawable.ic_play, Ui.VIOLET) {
            safeOpen(Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        act("Share", R.drawable.ic_share, 0xFF0EA5E9.toInt()) {
            safeOpen(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, m.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share recording"))
        }
        act("Delete", R.drawable.ic_delete, Ui.RED) {
            AlertDialog.Builder(this, DIALOG).setTitle("Delete this recording?").setMessage(m.name)
                .setPositiveButton("Delete") { _, _ ->
                    toast(if (Cleaner.deleteOwn(contentResolver, m.uri)) "Deleted" else "Could not delete")
                    rebuild()
                }.setNegativeButton("Cancel", null).show()
        }
        c.addView(actions)
        return c
    }

    // ---------------------------------------------------------------- CLEANING

    private fun mediaKey(m: Cleaner.Media) = m.id * 2 + if (m.isVideo) 1 else 0

    private fun scan() {
        scanShots = Cleaner.screenshots(this)
        scanVideos = Cleaner.largeVideos(this)
        scanned = true
    }

    private fun cleaningPage(col: LinearLayout) {
        col.addView(banner(R.drawable.banner_clean, "Free up\nspace.", "Storage, cache and big files", 140))
        col.addView(spacer(12))

        val (total, free) = Cleaner.totalFree()
        val used = total - free
        val c = card()
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val ring = RingView(this)
        ring.progress = if (total > 0) used.toFloat() / total else 0f
        ring.big = "${(ring.progress * 100).toInt()}%"
        ring.small = "used"
        c.addView(ring, lp(dp(170), dp(170)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val s = Ui.text(this, "${Cleaner.fmt(used)} used of ${Cleaner.fmt(total)}  ·  ${Cleaner.fmt(free)} free", 13f, Ui.MUTED)
        s.gravity = Gravity.CENTER
        c.addView(s, lp(MATCH, WRAP))
        col.addView(c)

        val cache = Cleaner.cacheBytes(this)
        val rec = Cleaner.recordings(this)
        val c2 = card()
        c2.addView(cleanRow(R.drawable.i3d_clean, "NavAssist cache", Cleaner.fmt(cache), "Clear") {
            toast("Freed ${Cleaner.fmt(Cleaner.clearCache(this))}")
            rebuild()
        })
        c2.addView(divider())
        c2.addView(cleanRow(R.drawable.i3d_record, "Screen recordings", "${rec.size} file(s) · ${Cleaner.fmt(rec.sumOf { it.size })}", "Manage") { navigate(3, 1) })
        c2.addView(divider())
        c2.addView(cleanRow(R.drawable.i3d_settings, "Android storage manager", "Apps, downloads, system files", "Open") {
            safeOpen(Intent("android.os.storage.action.MANAGE_STORAGE"))
        })
        col.addView(c2)

        col.addView(label("FIND BIG FILES"))
        if (!Cleaner.hasMediaAccess(this)) {
            val c3 = card()
            c3.setPadding(dp(16), dp(14), dp(16), dp(14))
            c3.addView(Ui.text(this, "Scan screenshots and videos over 100 MB", 15f, Ui.TEXT, weight = 600))
            val d = Ui.text(this, "NavAssist needs access to your photos and videos to list them. Nothing is uploaded, and every delete asks for your confirmation.", 12.5f, Ui.MUTED)
            d.setPadding(0, dp(6), 0, dp(12))
            c3.addView(d)
            c3.addView(pill("Allow & scan") { requestPermissions(Cleaner.mediaPermissions(), 101) }, lp(WRAP, WRAP))
            col.addView(c3)
        } else {
            if (!scanned) scan()
            mediaSection(col, "Screenshots", scanShots)
            mediaSection(col, "Videos over 100 MB", scanVideos)
            val sel = (scanShots + scanVideos).filter { mediaKey(it) in picked }
            col.addView(bigButton(
                if (sel.isEmpty()) "Select files to delete" else "Delete ${sel.size} file(s) · ${Cleaner.fmt(sel.sumOf { it.size })}",
                R.drawable.ic_delete, Ui.RED) { deleteSelected() }, lp(MATCH, dp(54)).also { it.topMargin = dp(4); it.bottomMargin = dp(8) })
            col.addView(bigButton("Scan again", R.drawable.ic_search, 0xFF334155.toInt()) {
                picked.clear(); scan(); rebuild()
            }, lp(MATCH, dp(48)))
        }
        val note = Ui.text(this, "NavAssist does not offer a “RAM booster”. Android manages memory itself, and apps that claim to boost it do nothing useful.", 11.5f, Ui.MUTED)
        note.setPadding(dp(4), dp(16), dp(4), 0)
        col.addView(note)
    }

    private fun cleanRow(pic: Int, title: String, sub: String, action: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        val iv = ImageView(this)
        iv.setImageResource(pic)
        row.addView(iv, lp(dp(44), dp(44)).also { it.marginEnd = dp(14) })
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.addView(Ui.text(this, title, 15f, Ui.TEXT, weight = 600))
        t.addView(Ui.text(this, sub, 12.5f, Ui.MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(pill(action, onClick))
        return row
    }

    private fun mediaSection(col: LinearLayout, title: String, list: List<Cleaner.Media>) {
        val c = card()
        val head = Ui.text(this, "$title  ·  ${list.size} · ${Cleaner.fmt(list.sumOf { it.size })}", 14f, Ui.TEXT, weight = 600)
        head.setPadding(0, dp(12), 0, dp(8))
        c.addView(head)
        if (list.isEmpty()) {
            val e = Ui.text(this, "Nothing found", 12.5f, Ui.MUTED)
            e.setPadding(0, 0, 0, dp(12))
            c.addView(e)
        }
        for (m in list.take(25)) {
            c.addView(divider())
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(8), 0, dp(8))
            val cb = CheckBox(this)
            cb.isChecked = mediaKey(m) in picked
            cb.buttonTintList = ColorStateList.valueOf(Ui.VIOLET)
            cb.setOnCheckedChangeListener { _, on -> if (on) picked.add(mediaKey(m)) else picked.remove(mediaKey(m)); rebuild() }
            row.addView(cb)
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            val n = Ui.text(this, m.name, 13.5f, Ui.TEXT, weight = 500)
            n.maxLines = 1
            t.addView(n)
            t.addView(Ui.text(this, Cleaner.fmt(m.size), 11.5f, Ui.MUTED))
            row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            c.addView(row)
        }
        col.addView(c)
    }

    private fun deleteSelected() {
        val sel = (scanShots + scanVideos).filter { mediaKey(it) in picked }
        if (sel.isEmpty()) {
            toast("Tick the files you want to delete")
            return
        }
        try {
            val pi = MediaStore.createDeleteRequest(contentResolver, sel.map { it.uri })
            startIntentSenderForResult(pi.intentSender, 77, null, 0, 0, 0)
        } catch (e: Exception) {
            toast("Could not start delete: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- CONNECTIONS

    private fun connectionsPage(col: LinearLayout) {
        col.addView(banner(R.drawable.banner_connect, "Stay\nconnected.", "Status and one-tap panels", 140))
        col.addView(spacer(12))

        val wifi = try {
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        } catch (e: Exception) { null }
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected via Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected via mobile data"
            else -> "Connected"
        }
        @Suppress("DEPRECATION")
        val bt = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled } catch (e: SecurityException) { null }
        val loc = try { getSystemService(LocationManager::class.java).isLocationEnabled } catch (e: Exception) { null }
        val air = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

        fun onOff(v: Boolean?) = when (v) { true -> "On" to Ui.GREEN; false -> "Off" to Ui.MUTED; else -> "Tap to check" to Ui.MUTED }
        val c = card()
        val rows = listOf(
            Triple("Wi-Fi", R.drawable.i3d_connect, onOff(wifi)) to { safeOpen(Intent(Settings.Panel.ACTION_WIFI)) },
            Triple("Internet", R.drawable.i3d_home, net to (if (caps != null) Ui.GREEN else Ui.MUTED)) to { safeOpen(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
            Triple("Bluetooth", R.drawable.i3d_menu, onOff(bt)) to { safeOpen(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            Triple("Location", R.drawable.i3d_clean, onOff(loc)) to { safeOpen(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            Triple("Airplane mode", R.drawable.i3d_features, (if (air) "On" else "Off") to (if (air) Ui.AMBER else Ui.MUTED)) to { safeOpen(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) },
            Triple("Cast screen", R.drawable.i3d_design, ("Open" to Ui.MUTED)) to { safeOpen(Intent(Settings.ACTION_CAST_SETTINGS)) },
            Triple("NFC", R.drawable.i3d_about, ("Open" to Ui.MUTED)) to { safeOpen(Intent(Settings.ACTION_NFC_SETTINGS)) }
        )
        rows.forEachIndexed { i, (info, act) ->
            if (i > 0) c.addView(divider())
            val (name, pic, status) = info
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(12), 0, dp(12))
            row.background = Ui.ripple(ColorDrawable(0), dp(12).toFloat())
            val iv = ImageView(this)
            iv.setImageResource(pic)
            row.addView(iv, lp(dp(42), dp(42)).also { it.marginEnd = dp(14) })
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.addView(Ui.text(this, name, 15f, Ui.TEXT, weight = 600))
            t.addView(Ui.text(this, status.first, 12.5f, status.second))
            row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(Ui.text(this, "›", 24f, Ui.MUTED))
            row.setOnClickListener { act() }
            c.addView(row)
        }
        col.addView(c)
        col.addView(Ui.text(this, "Android no longer lets apps switch Wi-Fi, Bluetooth or airplane mode directly. NavAssist opens the system panel so it is one tap away.", 12f, Ui.MUTED))
    }

    // ---------------------------------------------------------------- AI

    private fun aiPage(col: LinearLayout) {
        col.addView(banner(R.drawable.banner_ai, "Talk to\nyour AI.", "Voice and text, over any app", 140))
        col.addView(spacer(12))
        val cfg = AiConfig.load(this)
        val c = card()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(0, dp(10), 0, dp(10))
        val ic = ImageView(this)
        ic.setImageResource(R.drawable.i3d_ai)
        head.addView(ic, lp(dp(52), dp(52)).also { it.marginEnd = dp(14) })
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.addView(Ui.text(this, if (cfg.ready) cfg.provider.label else "Not set up yet", 16f, Ui.TEXT, weight = 600))
        t.addView(Ui.text(this, if (cfg.ready) "Model: ${cfg.model}  ·  key saved" else "Add an API key to start chatting", 12.5f, Ui.MUTED))
        head.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        c.addView(head)
        col.addView(c)
        col.addView(bigButton("Open AI chat", R.drawable.ic_sparkle, Ui.VIOLET) {
            startActivity(Intent(this, AiChatActivity::class.java))
        }, lp(MATCH, dp(54)).also { it.topMargin = dp(4); it.bottomMargin = dp(8) })
        col.addView(bigButton(if (cfg.ready) "Change provider or key" else "Set up AI provider", R.drawable.ic_settings, 0xFF334155.toInt()) {
            showProviderDialog()
        }, lp(MATCH, dp(50)))
        col.addView(label("VOICE LANGUAGE"))
        val cur = Prefs.of(this).getString("voice_lang", null) ?: ""
        col.addView(chips(voiceLangs.map { it.first }, voiceLangs.indexOfFirst { it.second == cur }.coerceAtLeast(0)) {
            val tag = voiceLangs[it].second
            if (tag.isEmpty()) Prefs.of(this).edit().remove("voice_lang").apply() else Prefs.setVoiceLang(this, tag)
            rebuild()
        })
        col.addView(spacer(10))
        val c2 = card()
        c2.addView(linkRow("Fallback app", (Apps.label(this, Prefs.aiPkg(this)) ?: "None") + "  ·  used only if no API key is set") {
            pickApp("Fallback AI app") { Prefs.setAiPkg(this, it) }
        })
        col.addView(c2)
        col.addView(Ui.text(this, "A ChatGPT or Claude app subscription is not an API key. Create a key on the provider’s developer site.", 12f, Ui.MUTED))
    }

    // ---------------------------------------------------------------- FEATURES

    private class Feature(val title: String, val desc: String, val pic: Int, val page: Int, val sub: Int = 0)

    private fun featuresPage(col: LinearLayout) {
        col.addView(sceneBanner("tiles", "Everything\nin one place.", "Tap a feature to open it", 220, R.drawable.banner_menu))
        col.addView(spacer(8))
        val features = listOf(
            Feature("Floating menu", "A glass panel with up to 31 buttons: Home, Back, Recents, Power, Volume and more.", R.drawable.i3d_menu, 1, 1),
            Feature("Orb gestures", "Single tap, double tap and long press can each run any action.", R.drawable.i3d_gestures, 1, 2),
            Feature("Layouts", "3×3, 3×4 or 4×4 grids with swipeable pages and a live preview.", R.drawable.i3d_menu, 1, 0),
            Feature("Themes and 3D orbs", "8 themes, 12 accent colors, 3D orb pictures, symbols and emoji.", R.drawable.i3d_design, 2),
            Feature("Volume mixer", "Separate sliders for media, calls, ringtone and alarm, plus ringer mode.", R.drawable.i3d_settings, 1, 1),
            Feature("Screen recorder", "HD video at 30 or 60 fps, mic or mute, saved in Movies/NavAssist.", R.drawable.i3d_record, 3),
            Feature("AI voice chat", "Talk or type to ChatGPT, Claude, Gemini or any compatible AI.", R.drawable.i3d_ai, 6),
            Feature("Cleaning", "Free space, cache, screenshots and big videos in one place.", R.drawable.i3d_clean, 4),
            Feature("Connections", "Wi-Fi, Bluetooth, mobile data, location and airplane mode at a glance.", R.drawable.i3d_connect, 5),
            Feature("Favorite apps", "One-tap access to up to 12 apps from the menu.", R.drawable.i3d_favorites, 1, 3),
            Feature("Recents fix", "Swipe-gesture fallback for phones that ignore the Recents action.", R.drawable.i3d_settings, 8, 2)
        )
        for (f in features) {
            val c = card()
            c.setPadding(dp(14), dp(14), dp(14), dp(14))
            c.background = Ui.ripple(Ui.card3d(this, 18), dp(18).toFloat())
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val iv = ImageView(this)
            iv.setImageResource(f.pic)
            row.addView(iv, lp(dp(56), dp(56)).also { it.marginEnd = dp(14) })
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.addView(Ui.text(this, f.title, 15.5f, Ui.TEXT, weight = 700))
            val d = Ui.text(this, f.desc, 12.5f, Ui.MUTED)
            d.setPadding(0, dp(3), 0, 0)
            t.addView(d)
            row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(Ui.text(this, "›", 24f, Ui.MUTED))
            c.addView(row)
            c.setOnClickListener { navigate(f.page, f.sub) }
            col.addView(c)
        }
    }

    // ---------------------------------------------------------------- SETTINGS

    private fun settingsPage(col: LinearLayout) {
        col.addView(banner(R.drawable.banner_settings, "Set up &\nfine-tune.", "Permissions, advanced options, tests", 140))
        col.addView(spacer(12))
        subTabBar(col)
        when (sub[8]) {
            0 -> settingsSetup(col)
            1 -> settingsAdvanced(col)
            else -> settingsTest(col)
        }
    }

    private fun settingsSetup(col: LinearLayout) {
        val setup = card()
        setup.addView(stepRow(1, "Permissions", "Notifications and microphone (for voice chat)", hasNotif() && hasMic(), "Allow") { requestRuntimePermissions() })
        setup.addView(divider())
        setup.addView(stepRow(2, "Display over other apps", "Needed to draw the floating orb", Settings.canDrawOverlays(this), "Open") {
            safeOpen(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        setup.addView(divider())
        setup.addView(stepRow(3, "Accessibility service", "Needed for Back, Recents, Lock, Power", Access.isEnabled(this),
            if (Access.canWriteSecure(this)) "Enable now" else "Enable") { openAccessibility() })
        setup.addView(divider())
        setup.addView(stepRow(4, "Do Not Disturb access", "Optional: ringtone and Silent in the mixer", hasDnd(), "Open") {
            safeOpen(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        setup.addView(divider())
        setup.addView(stepRow(5, "Modify system settings", "Optional: brightness, timeout, rotation", Settings.System.canWrite(this), "Open") {
            safeOpen(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        })
        col.addView(setup)
        if (!Access.isEnabled(this)) col.addView(restrictedCard())

        col.addView(label("SET UP WITH A COMPUTER (ADB)"))
        val cmds = "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow\n" +
            "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n" +
            "adb shell settings put secure enabled_accessibility_services $packageName/$packageName.NavAccessibilityService\n" +
            "adb shell settings put secure accessibility_enabled 1\n" +
            "adb shell cmd notification allow_dnd $packageName\n" +
            "adb shell appops set $packageName WRITE_SETTINGS allow"
        val c = card()
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        val code = Ui.text(this, cmds, 10.5f, 0xFFCBD5E1.toInt())
        code.typeface = Typeface.MONOSPACE
        code.setTextIsSelectable(true)
        c.addView(code)
        c.addView(pill("Copy commands") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("NavAssist ADB", cmds))
            toast("Copied")
        }, lp(WRAP, WRAP).also { it.topMargin = dp(10) })
        col.addView(c)
    }

    private fun settingsAdvanced(col: LinearLayout) {
        val c = card()
        c.addView(switchRow("Start on boot", "Brings the orb back after a restart", Prefs.startOnBoot(this)) { Prefs.setStartOnBoot(this, it) })
        c.addView(divider())
        c.addView(linkRow("Keep NavAssist running", "Exclude it from battery optimization") {
            safeOpen(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        c.addView(divider())
        c.addView(linkRow("Accessibility settings", "Turn NavAssist on or off") { openAccessibility() })
        c.addView(divider())
        c.addView(linkRow("App info", "Permissions, storage, restricted settings") {
            safeOpen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        col.addView(c)
    }

    private fun settingsTest(col: LinearLayout) {
        settingChips(col, "RECENTS METHOD", recentsModes, Prefs.recentsMode(this)) { Prefs.setRecentsMode(this, it) }
        col.addView(Ui.text(this, "Auto tries the system action first, then a swipe-up-and-hold gesture if the phone ignores it.", 12f, Ui.MUTED))
        col.addView(bigButton("Test Recents now", R.drawable.ic_recents, Ui.VIOLET) { testRecents() },
            lp(MATCH, dp(52)).also { it.topMargin = dp(10); it.bottomMargin = dp(8) })
        col.addView(bigButton("Screenshot in 4 seconds", R.drawable.ic_crop, 0xFF0EA5E9.toInt()) { screenshotSoon() },
            lp(MATCH, dp(52)).also { it.bottomMargin = dp(8) })
        col.addView(bigButton("Hide / show orb", R.drawable.ic_visibility_off, 0xFF334155.toInt()) { sendOverlay(OverlayService.ACTION_TOGGLE) },
            lp(MATCH, dp(52)))
        col.addView(label("THIS PHONE"))
        val nav = when (Settings.Secure.getInt(contentResolver, "navigation_mode", -1)) {
            0 -> "3-button"; 1 -> "2-button"; 2 -> "gesture"; else -> "unknown"
        }
        val c = card()
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.addView(Ui.text(this, "${Build.MANUFACTURER} ${Build.MODEL}", 15f, Ui.TEXT, weight = 600))
        c.addView(Ui.text(this, "Android ${Build.VERSION.RELEASE}  ·  navigation: $nav\nAccessibility service: ${if (Access.isRunning()) "running" else "not running"}", 12.5f, Ui.MUTED))
        col.addView(c)
    }

    // ---------------------------------------------------------------- ABOUT

    private fun aboutPage(col: LinearLayout) {
        col.addView(sceneBanner("rings", "NavAssist", "Version 4.0\nNo ads · No tracking", 220, R.drawable.banner_settings))
        col.addView(spacer(8))
        val c = card()
        c.setPadding(dp(16), dp(16), dp(16), dp(16))
        c.addView(Ui.text(this, "Why NavAssist exists", 16f, Ui.TEXT, weight = 700))
        val d = Ui.text(this, "Phones break. A cracked power button or dead volume key should not mean a dead phone. NavAssist puts Home, Back, Recents, Power, Volume, screenshots, a screen recorder and an AI assistant on a floating orb, so any phone stays usable.", 13f, Ui.MUTED)
        d.setPadding(0, dp(8), 0, 0)
        c.addView(d)
        col.addView(c)

        col.addView(label("PRIVACY"))
        val c1 = card()
        c1.setPadding(dp(16), dp(14), dp(16), dp(14))
        c1.addView(Ui.text(this, "The Accessibility Service only performs the actions you tap. It never reads your screen. Chats go only to the AI provider you choose, with your own key. Nothing is sent to us.", 12.5f, Ui.MUTED))
        col.addView(c1)

        col.addView(label("LINKS"))
        val c2 = card()
        c2.addView(linkRow("Android accessibility help", "Official Google guide") {
            safeOpen(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.google.com/accessibility/android")))
        })
        c2.addView(divider())
        c2.addView(linkRow("Contact the developer", "Send feedback by email") {
            safeOpen(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:your-email@example.com")).putExtra(Intent.EXTRA_SUBJECT, "NavAssist feedback"))
        })
        c2.addView(divider())
        c2.addView(linkRow("Share NavAssist", "Help someone with a broken button") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "NavAssist adds floating Home, Back, Recents, Power and Volume buttons plus an AI voice assistant for phones with broken hardware buttons.")
            safeOpen(Intent.createChooser(send, "Share NavAssist"))
        })
        col.addView(c2)

        col.addView(label("OPEN-SOURCE LICENCES"))
        val c3 = card()
        c3.setPadding(dp(16), dp(14), dp(16), dp(14))
        c3.addView(Ui.text(this, "Inter font © The Inter Project Authors, SIL Open Font License 1.1.\nThree.js © three.js authors, MIT License (used for the 3D scenes).", 12.5f, Ui.MUTED))
        col.addView(c3)
    }

    // ---------------------------------------------------------------- shared components

    private fun spacer(h: Int): View {
        val v = View(this)
        v.layoutParams = lp(MATCH, dp(h))
        return v
    }

    private fun label(s: String): View {
        val t = Ui.text(this, s, 11.5f, Ui.MUTED, weight = 600)
        t.letterSpacing = 0.12f
        t.setPadding(dp(4), dp(14), 0, dp(8))
        return t
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.card3d(this, 18)
        c.elevation = dp(3).toFloat()
        c.setPadding(dp(16), dp(6), dp(16), dp(6))
        val p = lp(MATCH, WRAP)
        p.bottomMargin = dp(12)
        c.layoutParams = p
        return c
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(Ui.BORDER)
        v.layoutParams = lp(MATCH, 1)
        return v
    }

    private fun banner(res: Int, title: String, sub: String, heightDp: Int): View {
        val box = FrameLayout(this)
        val img = ImageView(this)
        img.setImageResource(res)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = Ui.card3d(this, 22)
        img.clipToOutline = true
        box.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.gravity = Gravity.CENTER_VERTICAL
        txt.setPadding(dp(18), dp(10), dp(8), dp(10))
        txt.addView(Ui.text(this, title, 20f, Ui.TEXT, weight = 700))
        val s = Ui.text(this, sub, 12f, Ui.MUTED)
        s.setPadding(0, dp(6), 0, 0)
        txt.addView(s)
        box.addView(txt, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.52f).toInt(), MATCH))
        box.layoutParams = lp(MATCH, dp(heightDp))
        box.elevation = dp(4).toFloat()
        return box
    }

    private fun chips(items: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipChildren = false
        hs.clipToPadding = false
        hs.setPadding(0, dp(2), 0, dp(8))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        items.forEachIndexed { i, s ->
            val sel = i == selected
            val t = Ui.text(this, s, 13f, if (sel) Color.WHITE else Ui.MUTED, weight = 600)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(18), dp(10), dp(18), dp(10))
            t.background = if (sel) Ui.button3d(this, Ui.VIOLET, 12) else Ui.ripple(Ui.card3d(this, 12), dp(12).toFloat())
            if (sel) Ui.lift(t, this, Ui.VIOLET, 5)
            t.setOnClickListener { onSelect(i) }
            row.addView(t, lp(WRAP, WRAP).also { it.marginEnd = dp(8) })
        }
        hs.addView(row)
        return hs
    }

    private fun bigButton(text: String, icon: Int, color: Int, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.background = Ui.button3d(this, color, 16)
        Ui.lift(row, this, color, 8)
        val iv = ImageView(this)
        iv.setImageResource(icon)
        row.addView(iv, lp(dp(22), dp(22)).also { it.marginEnd = dp(10) })
        row.addView(Ui.text(this, text, 16f, Color.WHITE, weight = 600))
        row.setOnClickListener { onClick() }
        return row
    }

    private fun miniTile(icon: Int, name: String, onClick: () -> Unit): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(dp(6), dp(14), dp(6), dp(12))
        c.background = Ui.ripple(Ui.card3d(this, 16), dp(16).toFloat())
        c.elevation = dp(3).toFloat()
        val iv = ImageView(this)
        iv.setImageResource(icon)
        iv.setPadding(dp(11), dp(11), dp(11), dp(11))
        iv.background = Ui.button3d(this, Ui.VIOLET, 14)
        c.addView(iv, lp(dp(46), dp(46)))
        val t = Ui.text(this, name, 12f, Ui.TEXT, weight = 500)
        t.setPadding(0, dp(8), 0, 0)
        c.addView(t)
        c.setOnClickListener { onClick() }
        return c
    }

    /** Tile that shows one of the pre-rendered 3D icon pictures. */
    private fun miniTilePic(pic: Int, name: String, onClick: () -> Unit): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(dp(6), dp(12), dp(6), dp(10))
        c.background = Ui.ripple(Ui.card3d(this, 16), dp(16).toFloat())
        c.elevation = dp(3).toFloat()
        val iv = ImageView(this)
        iv.setImageResource(pic)
        c.addView(iv, lp(dp(52), dp(52)))
        val t = Ui.text(this, name, 12f, Ui.TEXT, weight = 500)
        t.setPadding(0, dp(6), 0, 0)
        c.addView(t)
        c.setOnClickListener { onClick() }
        return c
    }

    private fun pill(text: String, onClick: () -> Unit): View {
        val t = Ui.text(this, text, 13f, Color.WHITE, weight = 600)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(9), dp(16), dp(9))
        t.background = Ui.button3d(this, Ui.VIOLET, 10)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun stepRow(n: Int, title: String, sub: String, done: Boolean, action: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        val badge = Ui.text(this, if (done) "✓" else n.toString(), 15f, Color.WHITE, weight = 600)
        badge.gravity = Gravity.CENTER
        badge.background = if (done) Ui.oval(0xFF059669.toInt()) else Ui.oval(0x336366F1, Ui.VIOLET, dp(1))
        row.addView(badge, lp(dp(34), dp(34)).also { it.marginEnd = dp(14) })
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(Ui.text(this, title, 15f, Ui.TEXT, weight = 600))
        txt.addView(Ui.text(this, sub, 12.5f, Ui.MUTED))
        row.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (done) row.addView(Ui.text(this, "Done", 12f, Ui.GREEN, weight = 600)) else row.addView(pill(action, onClick))
        return row
    }

    private fun restrictedCard(): View {
        val c = card()
        c.background = Ui.shape(0x1AFBBF24, dp(18).toFloat(), 0x55FBBF24, dp(1))
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.addView(Ui.text(this, "Accessibility says “Restricted setting”?", 15f, Ui.AMBER, weight = 600))
        val body = Ui.text(
            this,
            "Android blocks this switch for apps installed outside the Play Store. Unlock it once:\n\n" +
                "1.  Tap “Open App info” below\n" +
                "2.  Tap ⋮ (top-right) → “Allow restricted settings”\n" +
                "3.  Confirm, come back and enable the service\n\n" +
                "No ⋮ menu? Tap NavAssist once in Accessibility settings first, then try again. " +
                "Or use the ADB commands in Settings → Setup.",
            13f, Ui.TEXT
        )
        body.setPadding(0, dp(8), 0, dp(12))
        c.addView(body)
        c.addView(pill("Open App info") {
            safeOpen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, lp(WRAP, WRAP))
        return c
    }

    private fun linkRow(title: String, sub: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(13), 0, dp(13))
        row.background = Ui.ripple(ColorDrawable(0), dp(12).toFloat())
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(Ui.text(this, title, 15f, Ui.TEXT, weight = 600))
        txt.addView(Ui.text(this, sub, 12.5f, Ui.MUTED))
        row.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(Ui.text(this, "›", 24f, Ui.MUTED))
        row.setOnClickListener { onClick() }
        return row
    }

    private fun styledSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = Switch(this)
        sw.isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(Ui.VIOLET, 0xFF6B7280.toInt()))
        sw.trackTintList = ColorStateList(states, intArrayOf(0x666366F1, Ui.BORDER))
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        return sw
    }

    private fun switchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(13), 0, dp(13))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(Ui.text(this, title, 15f, Ui.TEXT, weight = 600))
        txt.addView(Ui.text(this, sub, 12.5f, Ui.MUTED))
        row.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(styledSwitch(checked, onChange))
        return row
    }

    private fun sliderRow(title: String, min: Int, max: Int, value: Int, unit: String, onChange: (Int) -> Unit): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(13), 0, dp(10))
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.addView(Ui.text(this, title, 15f, Ui.TEXT, weight = 600), LinearLayout.LayoutParams(0, WRAP, 1f))
        val valueView = Ui.text(this, "$value$unit", 13f, Ui.MUTED)
        top.addView(valueView)
        col.addView(top)
        val sb = SeekBar(this)
        sb.min = min
        sb.max = max
        sb.progress = value.coerceIn(min, max)
        val states = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())
        sb.progressTintList = ColorStateList(states, intArrayOf(Ui.VIOLET, Ui.VIOLET))
        sb.thumbTintList = ColorStateList(states, intArrayOf(Ui.CYAN, Ui.CYAN))
        sb.progressBackgroundTintList = ColorStateList.valueOf(0xFF2B3647.toInt())
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                valueView.text = "$p$unit"
                if (fromUser) onChange(p)
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        col.addView(sb, lp(MATCH, WRAP))
        return col
    }

    // ------------------------------------------------------------------ actions

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun safeOpen(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("Could not open that page on this phone. Try the ADB commands in Settings → Setup.")
        }
    }

    private fun sendOverlay(action: String) {
        if (!OverlayService.running) {
            toast("Start the floating assistant first")
            return
        }
        startService(Intent(this, OverlayService::class.java).setAction(action))
    }

    private fun toggleOverlay() {
        if (OverlayService.running) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                toast("Allow “Display over other apps” first")
                return
            }
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        handler.postDelayed({ refresh(true) }, 400)
    }

    /** Hides the orb, gives you time to open the screen you want, then takes a screenshot. */
    private fun screenshotSoon() {
        val svc = NavAccessibilityService.instance
        if (svc == null) {
            toast("Turn on the accessibility service first")
            return
        }
        if (OverlayService.running) startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE))
        toast("Screenshot in 4 seconds. Open the screen you want.")
        moveTaskToBack(true)
        handler.postDelayed({ svc.run(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 4000)
        handler.postDelayed({
            if (OverlayService.running) startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW))
        }, 6500)
    }

    /** Shows the required disclosure before sending the user to Accessibility settings. */
    private fun openAccessibility() {
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Accessibility permission")
            .setMessage(
                "NavAssist uses Android’s Accessibility Service only to perform the system actions " +
                    "you tap in its floating menu: Back, Recents, Notifications, Quick settings, " +
                    "Lock screen, Power menu and Screenshot. If your phone ignores the normal Recents " +
                    "action, it can perform a swipe gesture for you.\n\n" +
                    "It does not read your screen, record what you type, or collect or share any data."
            )
            .setPositiveButton("Agree & continue") { _, _ ->
                if (Access.canWriteSecure(this) && Access.enableSelf(this)) {
                    toast("Accessibility service enabled")
                    refresh(true)
                } else {
                    safeOpen(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun testRecents() {
        val svc = NavAccessibilityService.instance
        if (svc == null) {
            toast("Turn on the accessibility service first")
            return
        }
        svc.openRecents(Prefs.recentsMode(this)) { how ->
            val msg = when (how) {
                "system" -> "Recents opened with the system action"
                "fallback" -> "System action was ignored, so the swipe gesture was used"
                "gesture" -> "Recents requested with a swipe gesture"
                "launcher" -> "Recents opened with a launcher shortcut"
                else -> "This method did not work. Try another Recents method."
            }
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun showProviderDialog() {
        val ps = Provider.values()
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Choose your AI")
            .setItems(ps.map { it.label }.toTypedArray()) { _, i -> showKeyDialog(ps[i]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showKeyDialog(p: Provider) {
        val cur = AiConfig.load(this)
        val same = cur.provider == p
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22), dp(8), dp(22), 0)

        fun field(hint: String, value: String, secret: Boolean): EditText {
            val e = EditText(this)
            e.hint = hint
            e.setText(value)
            e.isSingleLine = true
            e.inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            box.addView(e, lp(MATCH, WRAP))
            return e
        }
        box.addView(Ui.text(this, "Get a key at ${p.keyUrl}. The key is encrypted on this phone and sent only to the provider you choose.", 12.5f, Ui.MUTED))
        val key = field("API key", if (same) cur.key else "", true)
        val model = field("Model (default: ${p.defaultModel.ifBlank { "enter one" }})", if (same) cur.model else "", false)
        val base = if (p == Provider.CUSTOM) field("Base URL, e.g. https://openrouter.ai/api/v1", if (same) cur.baseUrl else "", false) else null

        AlertDialog.Builder(this, DIALOG)
            .setTitle(p.label)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                AiConfig.save(this, p, model.text.toString().ifBlank { p.defaultModel }, base?.text?.toString() ?: "", key.text.toString())
                toast("Saved. Tap the AI button in the floating menu to chat.")
                refresh(true)
            }
            .setNeutralButton("Get key") { _, _ ->
                if (p != Provider.CUSTOM) safeOpen(Intent(Intent.ACTION_VIEW, Uri.parse("https://" + p.keyUrl)))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickFavorites() {
        val apps = Apps.launchables(this)
        val cur = Prefs.favorites(this).toSet()
        val checked = BooleanArray(apps.size) { apps[it].pkg in cur }
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Favorite apps (up to 12)")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Save") { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.pkg }
                if (chosen.size > 12) toast("Only the first 12 are shown")
                Prefs.setFavorites(this, chosen.take(12))
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickApp(title: String, onPick: (String?) -> Unit) {
        val apps = Apps.launchables(this)
        val names = (listOf("None / system default") + apps.map { it.label }).toTypedArray()
        AlertDialog.Builder(this, DIALOG)
            .setTitle(title)
            .setItems(names) { _, i ->
                onPick(if (i == 0) null else apps[i - 1].pkg)
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
