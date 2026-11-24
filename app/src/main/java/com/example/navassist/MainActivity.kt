package com.example.navassist

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    // All runtime (dangerous) permissions the app needs, requested together.
    private val runtimePermissions = arrayOf(
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 16f }
        layout.addView(status)

        fun button(text: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply {
                this.text = text
                setOnClickListener { onClick() }
            })
        }

        button("Request permissions") { requestRuntimePermissions() }
        button("Overlay permission page") {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("Accessibility settings page") {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Start floating overlay") { startOverlay() }
        button("Stop floating overlay") { stopService(Intent(this, OverlayService::class.java)) }

        setContentView(ScrollView(this).apply { addView(layout) })

        requestRuntimePermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestRuntimePermissions() {
        val missing = runtimePermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Settings page unavailable (${e.javaClass.simpleName}). Use the ADB commands instead.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val a11y = NavAccessibilityService.instance != null
        val notif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        status.text = "Overlay permission: ${if (overlay) "OK" else "MISSING"}\n" +
            "Accessibility service: ${if (a11y) "RUNNING" else "NOT RUNNING"}\n" +
            "Notifications: ${if (notif) "OK" else "MISSING"}"
    }
}
