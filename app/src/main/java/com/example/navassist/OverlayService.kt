package com.example.navassist

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission missing", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) addOverlay()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("NavAssist running")
            .setContentText("Floating navigation buttons are active")
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(200)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.parseColor("#CC202020"), 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val handle = label("≡ Drag / tap to fold", Color.parseColor("#FF1E88E5"))
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = e.rawX; touchY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        if (moved) {
                            params.x = startX + dx
                            params.y = startY + dy
                            wm.updateViewLayout(container, params)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            buttons.visibility =
                                if (buttons.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                            wm.updateViewLayout(container, params)
                        }
                    }
                }
                return true
            }
        })
        container.addView(handle)

        fun add(text: String, action: () -> Unit) {
            buttons.addView(label(text, Color.parseColor("#FF424242")).apply {
                setOnClickListener { action() }
            })
        }

        add("Home") { goHome() }
        add("Back") { global(AccessibilityService.GLOBAL_ACTION_BACK) }
        add("Recents") { global(AccessibilityService.GLOBAL_ACTION_RECENTS) }
        add("Lock") { global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
        add("Power menu") { global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }
        add("Vol +") { volume(AudioManager.ADJUST_RAISE) }
        add("Vol −") { volume(AudioManager.ADJUST_LOWER) }
        add("Settings") { openSettings() }
        add("✕ Close") { stopSelf() }

        container.addView(buttons)
        root = container
        wm.addView(container, params)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun global(action: Int) {
        val svc = NavAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "Enable the NavAssist accessibility service", Toast.LENGTH_LONG).show()
        } else {
            svc.performGlobalAction(action)
        }
    }

    private fun volume(direction: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustSuggestedStreamVolume(
            direction,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open Settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun label(text: String, bg: Int) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(bg, 10)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
    }
}
