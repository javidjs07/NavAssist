package com.example.navassist

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

data class AppEntry(
    val label: String,
    val pkg: String,
    val activity: String,
    val info: ResolveInfo
)

object Apps {
    fun launchables(c: Context): List<AppEntry> {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name, it) }
            .filter { it.pkg != c.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun label(c: Context, pkg: String?): String? {
        if (pkg == null) return null
        return try {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            null
        }
    }

    fun launch(c: Context, pkg: String): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            c.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
