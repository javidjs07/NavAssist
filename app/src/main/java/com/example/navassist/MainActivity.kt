package com.example.navassist

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
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

    private fun requestRuntimePermissions() {
        val missing = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    private fun signature(): String = listOf(
        hasNotif(), Settings.canDrawOverlays(this), Access.isEnabled(this), Access.isRunning(),
        OverlayService.running, Prefs.enabledActions(this).size, Prefs.aiPkg(this),
        Prefs.favPkg(this), Access.canWriteSecure(this)
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

        val notif = hasNotif()
        val overlay = Settings.canDrawOverlays(this)
        val a11y = Access.isEnabled(this)

        col.addView(header())
        col.addView(primaryButton(
            if (OverlayService.running) "Stop floating assistant" else "Start floating assistant",
            OverlayService.running
        ) { toggleOverlay() })

        col.addView(sectionLabel("SETUP"))
        val setup = card()
        setup.addView(stepRow(1, "Notifications", "Shows the “running” notification", notif, "Allow") {
            requestRuntimePermissions()
        })
        setup.addView(divider())
        setup.addView(stepRow(2, "Display over other apps", "Needed to draw the floating orb", overlay, "Open") {
            safeOpen(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        setup.addView(divider())
        val a11yAction = if (Access.canWriteSecure(this)) "Enable now" else "Enable"
        setup.addView(stepRow(3, "Accessibility service", "Needed for Back, Recents, Lock, Power", a11y, a11yAction) {
            openAccessibility()
        })
        col.addView(setup)

        if (!a11y) col.addView(restrictedCard())

        col.addView(sectionLabel("CUSTOMIZE"))
        col.addView(customizeCard())

        col.addView(sectionLabel("MORE"))
        col.addView(moreCard())

        val foot = Ui.text(this, "NavAssist 2.1  •  No ads  •  No data collected", 12f, Ui.MUTED)
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, dp(18), 0, 0)
        col.addView(foot, lp(MATCH, WRAP))
        return col
    }

    private fun header(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(2), dp(8), 0, dp(22))

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_orb)
        logo.setPadding(dp(11), dp(11), dp(11), dp(11))
        logo.background = Ui.shape(0x336366F1, dp(16).toFloat(), 0x666366F1, dp(1))
        row.addView(logo, lp(dp(52), dp(52)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, 0, 0)
        col.addView(Ui.text(this, "NavAssist", 26f, Ui.TEXT, bold = true))
        col.addView(Ui.text(this, "Navigation for phones with broken buttons", 13f, Ui.MUTED))
        row.addView(col)
        return row
    }

    private fun primaryButton(text: String, danger: Boolean, onClick: () -> Unit): View {
        val fill = if (danger) Ui.RED else Ui.VIOLET
        val b = Ui.text(this, text, 16f, Color.WHITE, weight = 600)
        b.gravity = Gravity.CENTER
        b.background = Ui.ripple(Ui.shape(fill, dp(14).toFloat()), dp(14).toFloat())
        b.setOnClickListener { onClick() }
        val p = lp(MATCH, dp(56))
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
        val t = Ui.text(this, text, 13f, Color.WHITE, bold = true)
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

        val badge = Ui.text(this, if (done) "✓" else n.toString(), 15f, Color.WHITE, bold = true)
        badge.gravity = Gravity.CENTER
        badge.background = if (done) Ui.oval(0xFF059669.toInt()) else Ui.oval(0x336366F1, Ui.VIOLET, dp(1))
        val bl = lp(dp(34), dp(34))
        bl.marginEnd = dp(14)
        row.addView(badge, bl)

        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(Ui.text(this, title, 15f, Ui.TEXT, bold = true))
        txt.addView(Ui.text(this, sub, 12.5f, Ui.MUTED))
        row.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))

        if (done) row.addView(Ui.text(this, "Done", 12f, Ui.GREEN, bold = true)) else row.addView(pill(action, onClick))
        return row
    }

    private fun restrictedCard(): View {
        val c = card()
        c.background = Ui.shape(0x1AFBBF24, dp(16).toFloat(), 0x55FBBF24, dp(1))
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.addView(Ui.text(this, "Accessibility says “Restricted setting”?", 15f, Ui.AMBER, bold = true))
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
        val b = pill("Open App info") {
            safeOpen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        c.addView(b, lp(WRAP, WRAP))
        return c
    }

    private fun customizeCard(): View {
        val c = card()
        val enabled = Prefs.enabledActions(this)
        c.addView(linkRow("Menu buttons", "${enabled.size} of ${NavAction.values().size} shown") { pickButtons() })
        c.addView(divider())
        c.addView(linkRow("AI assistant app", Apps.label(this, Prefs.aiPkg(this)) ?: "System assistant") {
            pickApp("AI assistant app") { Prefs.setAiPkg(this, it) }
        })
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
                .putExtra(Intent.EXTRA_TEXT, "NavAssist adds floating Home, Back, Recents, Power and Volume buttons for phones with broken hardware buttons.")
            safeOpen(Intent.createChooser(send, "Share NavAssist"))
        })
        return c
    }

    private fun linkRow(title: String, sub: String, onClick: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(13), 0, dp(13))
        row.background = Ui.ripple(ColorDrawable(0), dp(12).toFloat())
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(Ui.text(this, title, 15f, Ui.TEXT, bold = true))
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
        txt.addView(Ui.text(this, title, 15f, Ui.TEXT, bold = true))
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
        top.addView(Ui.text(this, "Orb opacity when idle", 15f, Ui.TEXT, bold = true),
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
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Accessibility permission")
            .setMessage(
                "NavAssist uses Android’s Accessibility Service only to perform the system actions " +
                    "you tap in its floating menu: Back, Recents, Notifications, Quick settings, " +
                    "Lock screen, Power menu and Screenshot.\n\n" +
                    "It does not read your screen, record what you type, or collect or share any data."
            )
            .setPositiveButton("Agree & continue") { _, _ ->
                if (Access.canWriteSecure(this)) {
                    if (Access.enableSelf(this)) {
                        toast("Accessibility service enabled")
                        refresh(true)
                    } else {
                        safeOpen(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                } else {
                    safeOpen(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun pickButtons() {
        val all = NavAction.values()
        val cur = Prefs.enabledActions(this).toSet()
        val checked = BooleanArray(all.size) { all[it] in cur }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(names) { _, i ->
                onPick(if (i == 0) null else apps[i - 1].pkg)
                refresh(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
