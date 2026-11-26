package com.example.navassist

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "navassist_prefs"

    // keys the overlay service watches
    const val K_OPACITY = "opacity"
    const val K_ORB_SCALE = "orb_scale"
    const val K_ORB_ICON = "orb_icon"
    const val K_ORB_SHAPE = "orb_shape"
    const val K_ACCENT = "accent"

    private const val K_ACTIONS = "actions"
    private const val K_AI = "ai_pkg"
    private const val K_FAV = "fav_pkg"
    private const val K_FAVS = "fav_list"
    private const val K_BOOT = "boot"
    private const val K_X = "orb_x"
    private const val K_Y = "orb_y"
    private const val K_RECENTS = "recents_mode"
    private const val K_HAPTICS = "haptics"
    private const val K_SNAP = "snap"
    private const val K_PEEK = "peek"
    private const val K_PROVIDER = "ai_provider"
    private const val K_MODEL = "ai_model"
    private const val K_BASE = "ai_base"
    private const val K_KEY = "ai_key_enc"
    private const val K_LANG = "voice_lang"
    private const val K_THEME = "theme"
    private const val K_PANEL = "panel_opacity"
    private const val K_ANIM = "anim_speed"
    private const val K_LAYOUT = "layout"
    private const val K_TILES = "tiles"
    private const val K_TAP = "g_tap"
    private const val K_DOUBLE = "g_double"
    private const val K_LONG = "g_long"
    private const val K_HIDE_SECS = "hide_secs"
    private const val K_REC_RES = "rec_res"
    private const val K_REC_BITRATE = "rec_bitrate"
    private const val K_REC_FPS = "rec_fps"
    private const val K_REC_SOUND = "rec_sound"
    private const val K_REC_HIDE = "rec_hide_orb"

    fun of(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun i(c: Context, k: String, d: Int) = of(c).getInt(k, d)
    private fun setI(c: Context, k: String, v: Int) = of(c).edit().putInt(k, v).apply()
    private fun b(c: Context, k: String, d: Boolean) = of(c).getBoolean(k, d)
    private fun setB(c: Context, k: String, v: Boolean) = of(c).edit().putBoolean(k, v).apply()
    private fun s(c: Context, k: String, d: String) = of(c).getString(k, d) ?: d
    private fun setS(c: Context, k: String, v: String) = of(c).edit().putString(k, v).apply()

    // ---- menu buttons
    fun enabledActions(c: Context): List<NavAction> {
        val saved = of(c).getStringSet(K_ACTIONS, null) ?: return NavAction.defaults
        val list = NavAction.values().filter { it.name in saved }
        return if (list.isEmpty()) NavAction.defaults else list
    }

    fun setEnabledActions(c: Context, list: Collection<NavAction>) {
        of(c).edit().putStringSet(K_ACTIONS, list.map { it.name }.toSet()).apply()
    }

    /** 0 = 3x3, 1 = 3x4, 2 = 4x4 */
    fun layout(c: Context): Int = i(c, K_LAYOUT, 2)
    fun setLayout(c: Context, v: Int) = setI(c, K_LAYOUT, v)
    fun menuCols(c: Context): Int = if (layout(c) == 2) 4 else 3
    fun menuRows(c: Context): Int = if (layout(c) == 0) 3 else 4

    // ---- apps
    fun aiPkg(c: Context): String? = of(c).getString(K_AI, null)
    fun setAiPkg(c: Context, v: String?) = of(c).edit().putString(K_AI, v).apply()

    fun favPkg(c: Context): String? = of(c).getString(K_FAV, null)
    fun setFavPkg(c: Context, v: String?) = of(c).edit().putString(K_FAV, v).apply()

    fun favorites(c: Context): List<String> {
        val list = s(c, K_FAVS, "").split(',').filter { it.isNotBlank() }
        if (list.isNotEmpty()) return list
        return listOfNotNull(favPkg(c))
    }
    fun setFavorites(c: Context, v: List<String>) = setS(c, K_FAVS, v.joinToString(","))

    // ---- orb look
    fun opacity(c: Context): Int = i(c, K_OPACITY, 60)
    fun setOpacity(c: Context, v: Int) = setI(c, K_OPACITY, v)
    fun orbScale(c: Context): Int = i(c, K_ORB_SCALE, 100)
    fun setOrbScale(c: Context, v: Int) = setI(c, K_ORB_SCALE, v)
    fun orbIcon(c: Context): String = s(c, K_ORB_ICON, "ring")
    fun setOrbIcon(c: Context, v: String) = setS(c, K_ORB_ICON, v)
    /** 0 = circle, 1 = rounded square */
    fun orbShape(c: Context): Int = i(c, K_ORB_SHAPE, 0)
    fun setOrbShape(c: Context, v: Int) = setI(c, K_ORB_SHAPE, v)

    // ---- menu look
    fun theme(c: Context): Int = i(c, K_THEME, 0)
    fun setTheme(c: Context, v: Int) = setI(c, K_THEME, v)
    fun accent(c: Context): Int = i(c, K_ACCENT, 0)
    fun setAccent(c: Context, v: Int) = setI(c, K_ACCENT, v)
    fun panelOpacity(c: Context): Int = i(c, K_PANEL, 88)
    fun setPanelOpacity(c: Context, v: Int) = setI(c, K_PANEL, v)
    fun animSpeed(c: Context): Int = i(c, K_ANIM, 60)
    fun setAnimSpeed(c: Context, v: Int) = setI(c, K_ANIM, v)
    fun iconTiles(c: Context): Boolean = b(c, K_TILES, true)
    fun setIconTiles(c: Context, v: Boolean) = setB(c, K_TILES, v)

    // ---- behaviour
    fun startOnBoot(c: Context): Boolean = b(c, K_BOOT, false)
    fun setStartOnBoot(c: Context, v: Boolean) = setB(c, K_BOOT, v)
    fun haptics(c: Context): Boolean = b(c, K_HAPTICS, true)
    fun setHaptics(c: Context, v: Boolean) = setB(c, K_HAPTICS, v)
    fun snapToEdge(c: Context): Boolean = b(c, K_SNAP, true)
    fun setSnapToEdge(c: Context, v: Boolean) = setB(c, K_SNAP, v)
    /** Slides the idle orb half off the screen edge so it covers less (and shows less in screenshots). */
    fun peek(c: Context): Boolean = b(c, K_PEEK, true)
    fun setPeek(c: Context, v: Boolean) = setB(c, K_PEEK, v)

    /** 0 = auto, 1 = system action, 2 = swipe gesture, 3 = launcher shortcut */
    fun recentsMode(c: Context): Int = i(c, K_RECENTS, 0)
    fun setRecentsMode(c: Context, v: Int) = setI(c, K_RECENTS, v)

    fun orbX(c: Context): Int = i(c, K_X, -1)
    fun orbY(c: Context): Int = i(c, K_Y, -1)
    fun setOrb(c: Context, x: Int, y: Int) = of(c).edit().putInt(K_X, x).putInt(K_Y, y).apply()

    // ---- gestures: "MENU", "HIDE", "NONE" or a NavAction name
    fun tapAction(c: Context): String = s(c, K_TAP, "MENU")
    fun setTapAction(c: Context, v: String) = setS(c, K_TAP, v)
    fun doubleAction(c: Context): String = s(c, K_DOUBLE, "NONE")
    fun setDoubleAction(c: Context, v: String) = setS(c, K_DOUBLE, v)
    fun longAction(c: Context): String = s(c, K_LONG, "HIDE")
    fun setLongAction(c: Context, v: String) = setS(c, K_LONG, v)
    /** Seconds the orb stays hidden after "Hide orb"; 0 = until you bring it back. */
    fun hideSeconds(c: Context): Int = i(c, K_HIDE_SECS, 15)
    fun setHideSeconds(c: Context, v: Int) = setI(c, K_HIDE_SECS, v)

    // ---- screen recorder
    fun recRes(c: Context): Int = i(c, K_REC_RES, 0)
    fun setRecRes(c: Context, v: Int) = setI(c, K_REC_RES, v)
    fun recBitrate(c: Context): Int = i(c, K_REC_BITRATE, 1)
    fun setRecBitrate(c: Context, v: Int) = setI(c, K_REC_BITRATE, v)
    fun recFps(c: Context): Int = i(c, K_REC_FPS, 0)
    fun setRecFps(c: Context, v: Int) = setI(c, K_REC_FPS, v)
    /** 0 = mute, 1 = microphone */
    fun recSound(c: Context): Int = i(c, K_REC_SOUND, 0)
    fun setRecSound(c: Context, v: Int) = setI(c, K_REC_SOUND, v)
    fun recHideOrb(c: Context): Boolean = b(c, K_REC_HIDE, true)
    fun setRecHideOrb(c: Context, v: Boolean) = setB(c, K_REC_HIDE, v)

    // ---- cloud AI
    fun aiProvider(c: Context): String = s(c, K_PROVIDER, "OPENAI")
    fun aiModel(c: Context): String = s(c, K_MODEL, "")
    fun aiBase(c: Context): String = s(c, K_BASE, "")
    fun aiKeyEnc(c: Context): String = s(c, K_KEY, "")
    fun saveAi(c: Context, provider: String, model: String, base: String, keyEnc: String) {
        of(c).edit().putString(K_PROVIDER, provider).putString(K_MODEL, model)
            .putString(K_BASE, base).putString(K_KEY, keyEnc).apply()
    }

    fun voiceLang(c: Context): String =
        of(c).getString(K_LANG, null) ?: java.util.Locale.getDefault().toLanguageTag()
    fun setVoiceLang(c: Context, tag: String) = of(c).edit().putString(K_LANG, tag).apply()
}
