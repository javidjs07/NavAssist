package com.example.navassist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.startOnBoot(context) &&
            Settings.canDrawOverlays(context)
        ) {
            try {
                context.startForegroundService(Intent(context, OverlayService::class.java))
            } catch (e: Exception) {
                // Not allowed to start right now; the user can start it from the app.
            }
        }
    }
}
