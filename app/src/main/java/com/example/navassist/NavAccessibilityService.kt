package com.example.navassist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class NavAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastEventAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastEventAt = SystemClock.uptimeMillis()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Runs a global action. If the phone accepts it but no window changes within 700 ms
     * (some ROMs silently ignore Recents), [fallback] is run instead.
     * [report] receives "system", "fallback" or "failed" for diagnostics.
     */
    fun run(action: Int, fallback: (() -> Unit)? = null, report: ((String) -> Unit)? = null) {
        val before = SystemClock.uptimeMillis()
        val ok = performGlobalAction(action)
        if (fallback == null) {
            report?.invoke(if (ok) "system" else "failed")
            return
        }
        if (!ok) {
            fallback()
            report?.invoke("fallback")
            return
        }
        handler.postDelayed({
            if (lastEventAt >= before) {
                report?.invoke("system")
            } else {
                fallback()
                report?.invoke("fallback")
            }
        }, 700)
    }

    /** mode: 0 auto, 1 system action only, 2 swipe gesture, 3 launcher shortcut */
    fun openRecents(mode: Int, report: ((String) -> Unit)? = null) {
        when (mode) {
            1 -> run(GLOBAL_ACTION_RECENTS, null, report)
            2 -> report?.invoke(if (swipeUpAndHold()) "gesture" else "failed")
            3 -> report?.invoke(if (launcherRecents()) "launcher" else "failed")
            else -> run(GLOBAL_ACTION_RECENTS, {
                if (!swipeUpAndHold()) launcherRecents()
            }, report)
        }
    }

    fun openNotifications(report: ((String) -> Unit)? = null) =
        run(GLOBAL_ACTION_NOTIFICATIONS, { swipeDown(1) }, report)

    fun openQuickSettings(report: ((String) -> Unit)? = null) =
        run(GLOBAL_ACTION_QUICK_SETTINGS, { swipeDown(2) }, report)

    // ---------------------------------------------------------------- gestures

    /** The "swipe up and pause" gesture that opens Recents with gesture navigation. */
    private fun swipeUpAndHold(): Boolean {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        val x = w / 2f
        val endY = h * 0.55f
        val p1 = Path().apply {
            moveTo(x, h - 6f)
            lineTo(x, endY)
        }
        val s1 = GestureDescription.StrokeDescription(p1, 0, 300, true)
        val g1 = GestureDescription.Builder().addStroke(s1).build()
        return dispatchGesture(g1, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                val hold = Path().apply { moveTo(x, endY) }
                val s2 = s1.continueStroke(hold, 0, 450, false)
                dispatchGesture(GestureDescription.Builder().addStroke(s2).build(), null, null)
            }
        }, null)
    }

    /** Swipes down from the top edge [times] times (1 = notifications, 2 = quick settings). */
    private fun swipeDown(times: Int) {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(w / 2f, 8f)
            lineTo(w / 2f, h * 0.55f)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 260))
            .build()
        dispatchGesture(g, null, null)
        if (times > 1) {
            handler.postDelayed({ dispatchGesture(g, null, null) }, 600)
        }
    }

    /** Best effort: some launchers expose a Recents screen as a normal activity. */
    private fun launcherRecents(): Boolean {
        val candidates = listOf(
            "com.android.launcher" to "com.android.quickstep.RecentsActivity",
            "net.oneplus.launcher" to "com.android.quickstep.RecentsActivity",
            "com.oplus.launcher" to "com.android.quickstep.RecentsActivity",
            "com.google.android.apps.nexuslauncher" to "com.android.quickstep.RecentsActivity",
            "com.android.systemui" to "com.android.systemui.recents.RecentsActivity"
        )
        for ((pkg, cls) in candidates) {
            try {
                startActivity(Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // try the next one
            }
        }
        return false
    }

    companion object {
        @Volatile
        var instance: NavAccessibilityService? = null
    }
}
