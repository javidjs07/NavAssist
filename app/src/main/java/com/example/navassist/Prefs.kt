package com.example.navassist

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "navassist_prefs"
    const val K_OPACITY = "opacity"
    const val K_ORB_SIZE = "orb_size"
    private const val K_ACTIONS = "actions"
    private const val K_AI = "ai_pkg"
    private const val K_FAV = "fav_pkg"
    private const val K_BOOT = "boot"
    private const val K_X = "orb_x"
    private const val K_Y = "orb_y"
    private const val K_RECENTS = "recents_mode"
    private const val K_HAPTICS = "haptics"
    private const val K_SNAP = "snap"
    private const val K_PROVIDER = "ai_provider"
    private const val K_MODEL = "ai_model"
    private const val K_BASE = "ai_base"
    private const val K_KEY = "ai_key_enc"
    private const val K_LANG = "voice_lang"

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

    /** Optional fallback: open this app when the AI button is used without a cloud provider. */
    fun aiPkg(c: Context): String? = of(c).getString(K_AI, null)
    fun setAiPkg(c: Context, v: String?) = of(c).edit().putString(K_AI, v).apply()

    fun favPkg(c: Context): String? = of(c).getString(K_FAV, null)
    fun setFavPkg(c: Context, v: String?) = of(c).edit().putString(K_FAV, v).apply()

    fun opacity(c: Context): Int = of(c).getInt(K_OPACITY, 55)
    fun setOpacity(c: Context, v: Int) = of(c).edit().putInt(K_OPACITY, v).apply()

    /** 0 = small, 1 = medium, 2 = large */
    fun orbSize(c: Context): Int = of(c).getInt(K_ORB_SIZE, 1)
    fun setOrbSize(c: Context, v: Int) = of(c).edit().putInt(K_ORB_SIZE, v).apply()

    fun startOnBoot(c: Context): Boolean = of(c).getBoolean(K_BOOT, false)
    fun setStartOnBoot(c: Context, v: Boolean) = of(c).edit().putBoolean(K_BOOT, v).apply()

    fun haptics(c: Context): Boolean = of(c).getBoolean(K_HAPTICS, true)
    fun setHaptics(c: Context, v: Boolean) = of(c).edit().putBoolean(K_HAPTICS, v).apply()

    fun snapToEdge(c: Context): Boolean = of(c).getBoolean(K_SNAP, true)
    fun setSnapToEdge(c: Context, v: Boolean) = of(c).edit().putBoolean(K_SNAP, v).apply()

    /** 0 = auto (try everything), 1 = system action, 2 = swipe gesture, 3 = launcher shortcut */
    fun recentsMode(c: Context): Int = of(c).getInt(K_RECENTS, 0)
    fun setRecentsMode(c: Context, v: Int) = of(c).edit().putInt(K_RECENTS, v).apply()

    fun orbX(c: Context): Int = of(c).getInt(K_X, -1)
    fun orbY(c: Context): Int = of(c).getInt(K_Y, -1)
    fun setOrb(c: Context, x: Int, y: Int) = of(c).edit().putInt(K_X, x).putInt(K_Y, y).apply()

    // ---- cloud AI
    fun aiProvider(c: Context): String = of(c).getString(K_PROVIDER, "OPENAI") ?: "OPENAI"
    fun aiModel(c: Context): String = of(c).getString(K_MODEL, "") ?: ""
    fun aiBase(c: Context): String = of(c).getString(K_BASE, "") ?: ""
    fun aiKeyEnc(c: Context): String = of(c).getString(K_KEY, "") ?: ""
    fun saveAi(c: Context, provider: String, model: String, base: String, keyEnc: String) {
        of(c).edit().putString(K_PROVIDER, provider).putString(K_MODEL, model)
            .putString(K_BASE, base).putString(K_KEY, keyEnc).apply()
    }

    fun voiceLang(c: Context): String =
        of(c).getString(K_LANG, null) ?: java.util.Locale.getDefault().toLanguageTag()
    fun setVoiceLang(c: Context, tag: String) = of(c).edit().putString(K_LANG, tag).apply()
}
