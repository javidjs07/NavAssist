package com.example.navassist

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "navassist_prefs"
    const val K_OPACITY = "opacity"
    private const val K_ACTIONS = "actions"
    private const val K_AI = "ai_pkg"
    private const val K_FAV = "fav_pkg"
    private const val K_BOOT = "boot"
    private const val K_X = "orb_x"
    private const val K_Y = "orb_y"

    fun of(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabledActions(c: Context): List<NavAction> {
        val saved = of(c).getStringSet(K_ACTIONS, null) ?: return NavAction.defaults
        val list = NavAction.values().filter { it.name in saved }
        return if (list.isEmpty()) NavAction.defaults else list
    }

    fun setEnabledActions(c: Context, list: Collection<NavAction>) {
        of(c).edit().putStringSet(K_ACTIONS, list.map { it.name }.toSet()).apply()
    }

    fun aiPkg(c: Context): String? = of(c).getString(K_AI, null)
    fun setAiPkg(c: Context, v: String?) = of(c).edit().putString(K_AI, v).apply()

    fun favPkg(c: Context): String? = of(c).getString(K_FAV, null)
    fun setFavPkg(c: Context, v: String?) = of(c).edit().putString(K_FAV, v).apply()

    fun opacity(c: Context): Int = of(c).getInt(K_OPACITY, 55)
    fun setOpacity(c: Context, v: Int) = of(c).edit().putInt(K_OPACITY, v).apply()

    fun startOnBoot(c: Context): Boolean = of(c).getBoolean(K_BOOT, false)
    fun setStartOnBoot(c: Context, v: Boolean) = of(c).edit().putBoolean(K_BOOT, v).apply()

    fun orbX(c: Context): Int = of(c).getInt(K_X, -1)
    fun orbY(c: Context): Int = of(c).getInt(K_Y, -1)
    fun setOrb(c: Context, x: Int, y: Int) = of(c).edit().putInt(K_X, x).putInt(K_Y, y).apply()
}
