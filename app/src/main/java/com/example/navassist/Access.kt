package com.example.navassist

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object Access {
    private fun flat(c: Context): String =
        ComponentName(c, NavAccessibilityService::class.java).flattenToString()

    fun isRunning(): Boolean = NavAccessibilityService.instance != null

    /** True when the service is switched on in Android settings (or already running). */
    fun isEnabled(c: Context): Boolean {
        if (isRunning()) return true
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = flat(c)
        return s.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun canWriteSecure(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Works only after: adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS */
    fun enableSelf(c: Context): Boolean {
        return try {
            val cur = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val me = flat(c)
            val parts = cur.split(':').filter { it.isNotBlank() }
            if (parts.none { it.equals(me, ignoreCase = true) }) {
                Settings.Secure.putString(
                    c.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (parts + me).joinToString(":")
                )
            }
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
