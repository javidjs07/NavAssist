package com.example.navassist

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * NavAssist's own "recent tabs" list. It combines (1) the apps the accessibility service saw come to the
 * foreground and (2) Android's usage history when Usage access is allowed.
 */
object RecentApps {
    private const val FILE_KEY = "recent_apps"
    private val mru = ArrayList<String>()
    private var loaded = false
    private var homePkgs: Set<String>? = null

    private fun load(c: Context) {
        if (loaded) return
        loaded = true
        Prefs.of(c).getString(FILE_KEY, "")?.split(',')?.filter { it.isNotBlank() }?.let { mru.addAll(it) }
    }

    private fun home(c: Context): Set<String> {
        homePkgs?.let { return it }
        val h = c.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toSet()
        homePkgs = h
        return h
    }

    private fun usable(c: Context, pkg: String): Boolean =
        pkg != c.packageName && pkg != "com.android.systemui" && pkg !in home(c) &&
            c.packageManager.getLaunchIntentForPackage(pkg) != null

    fun hasUsageAccess(c: Context): Boolean = try {
        c.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    /** Called by the accessibility service whenever a window comes to the front. */
    @Synchronized
    fun record(c: Context, pkg: String) {
        load(c)
        if (mru.firstOrNull() == pkg || !usable(c, pkg)) return
        mru.remove(pkg)
        mru.add(0, pkg)
        while (mru.size > 24) mru.removeAt(mru.size - 1)
        Prefs.of(c).edit().putString(FILE_KEY, mru.joinToString(",")).apply()
    }

    @Synchronized
    fun list(c: Context, limit: Int = 12): List<String> {
        load(c)
        val out = LinkedHashSet<String>()
        for (p in mru) if (usable(c, p)) out.add(p)
        if (hasUsageAccess(c)) {
            try {
                val usm = c.getSystemService(UsageStatsManager::class.java)
                val now = System.currentTimeMillis()
                usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7L * 24 * 3600 * 1000, now)
                    .filter { it.lastTimeUsed > 0 }
                    .sortedByDescending { it.lastTimeUsed }
                    .forEach { if (usable(c, it.packageName)) out.add(it.packageName) }
            } catch (e: Exception) {
                // ignore
            }
        }
        return out.take(limit)
    }
}
