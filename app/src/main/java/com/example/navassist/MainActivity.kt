package com.example.navassist

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var scroll: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private var lastSig = ""

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
        "Device default" to "",
        "English (India)" to "en-IN",
        "English (US)" to "en-US",
        "తెలుగు (Telugu)" to "te-IN",
        "हिन्दी (Hindi)" to "hi-IN",
        "தமிழ் (Tamil)" to "ta-IN",
        "ಕನ್ನಡ (Kannada)" to "kn-IN"
    )
    private val recentsModes = listOf(
        "Auto (recommended)", "System action only", "Swipe gesture", "Launcher shortcut"
    )
    private val orbSizes = listOf("Small", "Medium", "Large")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.isNavigationBarContrastEnforced = false

        scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.isVerticalScrollBarEnabled = false
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        setContentView(scroll)

        requestRuntimePermissions()
        if (savedInstanceState == null && intent.getBooleanExtra("open_ai", false)) {
            handler.post { showProviderDialog() }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh(true)
        handler.postDelayed(ticker, 1200)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        refresh(true)
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
        hasNotif(), hasMic(), hasDnd(), Settings.canDrawOverlays(this), Access.isEnabled(this),
        Access.isRunning(), OverlayService.running, Prefs.enabledActions(this).size,
        Prefs.aiPkg(this), Prefs.favPkg(this), Access.canWriteSecure(this),
        Prefs.aiProvider(this), Prefs.aiModel(this), Prefs.aiKeyEnc(this).length,
        Prefs.voiceLang(this), Prefs.recentsMode(this), Prefs.orbSize(this)
    ).joinToString("|")

    private fun refresh(force: Boolean) {
        val sig = signature()
        if (!force && sig == lastSig) return
        lastSig = sig
        val y = scroll.scrollY
        scroll.removeAllViews()
        scroll.addView(buildContent())
        scroll.post { scroll.scrollTo(0, y) }
    }

    // ------------------------------------------------------------------ screen

    private fun buildContent(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(16), dp(20), dp(32))

        val overlay = Settings.canDrawOverlays(this)
        val a11y = Access.isEnabled(this)
        val running = OverlayService.running

        col.addView(header(running))
        col.addView(hero())
        col.addView(primaryButton(
            if (running) "Stop floating assistant" else "Start floating assistant", running
        ) { toggleOverlay() })

        col.addView(sectionLabel("SETUP"))
        val setup = card()
        setup.addView(stepRow(1, "Permissions", "Notifications and microphone (for voice chat)",
            hasNotif() && hasMic(), "Allow") { requestRuntimePermissions() })
        setup.addView(divider())
        setup.addView(stepRow(2, "Display over other apps", "Needed to draw the floating orb", overlay, "Open") {
            safeOpen(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        setup.addView(divider())
        val a11yAction = if (Access.canWriteSecure(this)) "Enable now" else "Enable"
        setup.addView(stepRow(3, "Accessibility service", "Needed for Back, Recents, Lock, Power", a11y, a11yAction) {
            openAccessibility()
        })
        setup.addView(divider())
        setup.addView(stepRow(4, "Do Not Disturb access", "Optional: ringtone and Silent in the volume mixer", hasDnd(), "Open") {
            safeOpen(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        col.addView(setup)

        if (!a11y) col.addView(restrictedCard())

        col.addView(sectionLabel("AI ASSISTANT"))
        col.addView(aiCard())

        col.addView(sectionLabel("CUSTOMIZE"))
        col.addView(customizeCard())

        col.addView(sectionLabel("ADVANCED"))
        col.addView(advancedCard())

        col.addView(sectionLabel("MORE"))
        col.addView(moreCard())

        val foot = Ui.text(this, "NavAssist 2.2  •  No ads  •  No tracking", 12f, Ui.MUTED)
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, dp(18), 0, 0)
        col.addView(foot, lp(MATCH, WRAP))
        return col
    }

    private fun header(running: Boolean): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(2), dp(8), 0, dp(18))

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_orb)
        logo.setPadding(dp(11), dp(11), dp(11), dp(11))
        logo.background = Ui.shape(0x336366F1, dp(16).toFloat(), 0x666366F1, dp(1))
        row.addView(logo, lp(dp(50), dp(50)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, 0, 0)
        col.addView(Ui.text(this, "NavAssist", 25f, Ui.TEXT, weight = 700))
        col.addView(Ui.text(this, "Navigation for phones with broken buttons", 12.5f, Ui.MUTED))
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))

        val chip = Ui.text(this, if (running) "● Active" else "● Stopped", 11.5f,
            if (running) Ui.GREEN else Ui.MUTED, weight = 600)
        chip.setPadding(dp(10), dp(5), dp(10), dp(5))
        chip.background = Ui.shape(Ui.SURFACE, dp(12).toFloat(), Ui.BORDER, dp(1))
        row.addView(chip)
        return row
    }

    private fun hero(): View {
        val box = FrameLayout(this)
        val img = ImageView(this)
        img.setImageResource(R.drawable.hero_banner)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = Ui.shape(Ui.SURFACE, dp(20).toFloat(), Ui.BORDER, dp(1))
        img.clipToOutline = true
        box.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))

        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.gravity = Gravity.CENTER_VERTICAL
        txt.setPadding(dp(18), dp(14), dp(8), dp(14))
        txt.addView(Ui.text(this, "Your buttons,\nright on screen.", 20f, Ui.TEXT, weight = 700))
        val sub = Ui.text(this, "Home · Back · Recents · Power\nVolume mixer · AI voice chat", 12f, Ui.MUTED)
        sub.setPadding(0, dp(8), 0, 0)
        txt.addView(sub)
        val w = (resources.displayMetrics.widthPixels * 0.52f).toInt()
        box.addView(txt, FrameLayout.LayoutParams(w, MATCH))

        val p = lp(MATCH, dp(168))
        p.bottomMargin = dp(14)
        box.layoutParams = p
        return box
    }

    private fun primaryButton(text: String, danger: Boolean, onClick: () -> Unit): View {
        val fill = if (danger) Ui.RED else Ui.VIOLET
        val b = Ui.text(this, text, 16f, Color.WHITE, weight = 600)
        b.gravity = Gravity.CENTER
        b.background = Ui.ripple(Ui.shape(fill, dp(14).toFloat()), dp(14).toFloat())
        b.setOnClickListener { onClick() }
        val p = lp(MATCH, dp(54))
        p.bottomMargin = dp(20)
        b.layoutParams = p
        return b
    }

    private fun sectionLabel(s: String): View {
        val t = Ui.text(this, s, 12f, Ui.MUTED, weight = 600)
        t.letterSpacing = 0.12f
        t.setPadding(dp(4), dp(8), 0, dp(8))
        return t
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.shape(Ui.SURFACE, dp(16).toFloat(), Ui.BORDER, dp(1))
        c.setPadding(dp(16), dp(6), dp(16), dp(6))
        val p = lp(MATCH, WRAP)
        p.bottomMargin = dp(14)
        c.layoutParams = p
        return c
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(Ui.BORDER)
        v.layoutParams = lp(MATCH, 1)
        return v
    }

    private fun pill(text: String, onClick: () -> Unit): View {
        val t = Ui.text(this, text, 13f, Color.WHITE, weight = 600)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(9), dp(16), dp(9))
        t.background = Ui.ripple(Ui.shape(Ui.VIOLET, dp(10).toFloat()), dp(10).toFloat())
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
        val bl = lp(dp(34), dp(34))
        bl.marginEnd = dp(14)
        row.addView(badge, bl)

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
        c.background = Ui.shape(0x1AFBBF24, dp(16).toFloat(), 0x55FBBF24, dp(1))
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.addView(Ui.text(this, "Accessibility says “Restricted setting”?", 15f, Ui.AMBER, weight = 600))
        val body = Ui.text(
            this,
            "Android blocks this switch for apps installed outside the Play Store. Unlock it once:\n\n" +
                "1.  Tap “Open App info” below\n" +
                "2.  Tap ⋮ (top-right) → “Allow restricted settings”\n" +
                "3.  Confirm, come back and tap “Enable” in step 3\n\n" +
                "No ⋮ menu? Tap NavAssist once in Accessibility settings first, then try again. " +
                "You can also use the ADB commands from the README, which work even when Settings is broken.",
            13f, Ui.TEXT
        )
        body.setPadding(0, dp(8), 0, dp(12))
        c.addView(body)
        c.addView(pill("Open App info") {
            safeOpen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, lp(WRAP, WRAP))
        return c
    }

    private fun aiCard(): View {
        val c = card()
        val cfg = AiConfig.load(this)
        c.addView(linkRow(
            "AI provider",
            if (cfg.ready) "${cfg.provider.label} · ${cfg.model}" else "Not set up. Tap to add your API key"
        ) { showProviderDialog() })
        c.addView(divider())
        c.addView(linkRow("Open the AI chat", "Type or talk. Opens over any app") {
            startActivity(Intent(this, AiChatActivity::class.java))
        })
        c.addView(divider())
        c.addView(linkRow("Voice language", voiceLangLabel()) { pickVoiceLang() })
        c.addView(divider())
        c.addView(linkRow("Fallback app", (Apps.label(this, Prefs.aiPkg(this)) ?: "None") + "  ·  used only if no API key is set") {
            pickApp("Fallback AI app") { Prefs.setAiPkg(this, it) }
        })
        return c
    }

    private fun customizeCard(): View {
        val c = card()
        val enabled = Prefs.enabledActions(this)
        c.addView(linkRow("Menu buttons", "${enabled.size} of ${NavAction.values().size} shown") { pickButtons() })
        c.addView(divider())
        c.addView(linkRow("Favorite app", Apps.label(this, Prefs.favPkg(this)) ?: "Not set") {
            pickApp("Favorite app") { Prefs.setFavPkg(this, it) }
        })
        c.addView(divider())
        c.addView(opacityRow())
        c.addView(divider())
        c.addView(switchRow("Start on boot", "Brings the orb back after a restart", Prefs.startOnBoot(this)) {
            Prefs.setStartOnBoot(this, it)
        })
        return c
    }

    private fun advancedCard(): View {
        val c = card()
        c.addView(linkRow("Recents method", recentsModes[Prefs.recentsMode(this).coerceIn(0, 3)]) { pickRecentsMode() })
        c.addView(divider())
        c.addView(linkRow("Test Recents now", "Uses the method above and tells you what worked") { testRecents() })
        c.addView(divider())
        c.addView(linkRow("Orb size", orbSizes[Prefs.orbSize(this).coerceIn(0, 2)]) { pickOrbSize() })
        c.addView(divider())
        c.addView(switchRow("Haptic feedback", "Short vibration on taps", Prefs.haptics(this)) { Prefs.setHaptics(this, it) })
        c.addView(divider())
        c.addView(switchRow("Snap to screen edge", "The orb glides to the nearest side", Prefs.snapToEdge(this)) {
            Prefs.setSnapToEdge(this, it)
        })
        c.addView(divider())
        val nav = when (Settings.Secure.getInt(contentResolver, "navigation_mode", -1)) {
            0 -> "3-button"; 1 -> "2-button"; 2 -> "gesture"; else -> "unknown"
        }
        val info = linkRow("Device info", "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL} · navigation: $nav") {}
        info.background = null
        c.addView(info)
        return c
    }

    private fun moreCard(): View {
        val c = card()
        c.addView(linkRow("Accessibility settings", "Turn NavAssist on or off") { openAccessibility() })
        c.addView(divider())
        c.addView(linkRow("Keep NavAssist running", "Exclude it from battery optimization") {
            safeOpen(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        c.addView(divider())
        c.addView(linkRow("App info", "Permissions, storage, restricted settings") {
            safeOpen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        c.addView(divider())
        c.addView(linkRow("Android accessibility help", "Official Google guide") {
            safeOpen(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.google.com/accessibility/android")))
        })
        c.addView(divider())
        c.addView(linkRow("Share NavAssist", "Help someone with a broken button") {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "NavAssist adds floating Home, Back, Recents, Power and Volume buttons plus an AI voice assistant for phones with broken hardware buttons.")
            safeOpen(Intent.createChooser(send, "Share NavAssist"))
        })
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
        val sw = Switch(this)
        sw.isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(Ui.VIOLET, 0xFF6B7280.toInt()))
        sw.trackTintList = ColorStateList(states, intArrayOf(0x666366F1, Ui.BORDER))
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(sw)
        return row
    }

    private fun opacityRow(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(13), 0, dp(10))
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.addView(Ui.text(this, "Orb opacity when idle", 15f, Ui.TEXT, weight = 600),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        val value = Ui.text(this, "${Prefs.opacity(this)}%", 13f, Ui.MUTED)
        top.addView(value)
        col.addView(top)
        val sb = SeekBar(this)
        sb.min = 20
        sb.max = 100
        sb.progress = Prefs.opacity(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())
        sb.progressTintList = ColorStateList(states, intArrayOf(Ui.VIOLET, Ui.VIOLET))
        sb.thumbTintList = ColorStateList(states, intArrayOf(Ui.CYAN, Ui.CYAN))
        sb.progressBackgroundTintList = ColorStateList.valueOf(0xFF2B3647.toInt())
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                value.text = "$p%"
                if (fromUser) Prefs.setOpacity(this@MainActivity, p)
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
            toast("Could not open that page on this phone. Try the ADB commands in the README.")
        }
    }

    private fun toggleOverlay() {
        if (OverlayService.running) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                toast("Step 2 first: allow “Display over other apps”")
                return
            }
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        handler.postDelayed({ refresh(true) }, 400)
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
            toast("Turn on the accessibility service first (step 3)")
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

    private fun pickRecentsMode() {
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Recents method")
            .setSingleChoiceItems(recentsModes.toTypedArray(), Prefs.recentsMode(this)) { d, i ->
                Prefs.setRecentsMode(this, i)
                d.dismiss()
                refresh(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickOrbSize() {
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Orb size")
            .setSingleChoiceItems(orbSizes.toTypedArray(), Prefs.orbSize(this)) { d, i ->
                Prefs.setOrbSize(this, i)
                d.dismiss()
                refresh(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun voiceLangLabel(): String {
        val saved = Prefs.of(this).getString("voice_lang", null) ?: return "Device default"
        return voiceLangs.firstOrNull { it.second == saved }?.first ?: saved
    }

    private fun pickVoiceLang() {
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Voice language")
            .setItems(voiceLangs.map { it.first }.toTypedArray()) { _, i ->
                val tag = voiceLangs[i].second
                if (tag.isEmpty()) Prefs.of(this).edit().remove("voice_lang").apply()
                else Prefs.setVoiceLang(this, tag)
                refresh(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                AiConfig.save(
                    this, p,
                    model.text.toString().ifBlank { p.defaultModel },
                    base?.text?.toString() ?: "",
                    key.text.toString()
                )
                toast("Saved. Tap the AI button in the floating menu to chat.")
                refresh(true)
            }
            .setNeutralButton("Get key") { _, _ ->
                if (p != Provider.CUSTOM) safeOpen(Intent(Intent.ACTION_VIEW, Uri.parse("https://" + p.keyUrl)))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickButtons() {
        val all = NavAction.values()
        val cur = Prefs.enabledActions(this).toSet()
        val checked = BooleanArray(all.size) { all[it] in cur }
        AlertDialog.Builder(this, DIALOG)
            .setTitle("Buttons in the menu")
            .setMultiChoiceItems(all.map { it.label }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Save") { _, _ ->
                Prefs.setEnabledActions(this, all.filterIndexed { i, _ -> checked[i] })
                refresh(true)
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
                refresh(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
