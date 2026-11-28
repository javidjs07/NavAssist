package com.example.navassist

import android.content.Context

data class MenuTheme(
    val name: String, val top: Int, val bottom: Int, val stroke: Int,
    val text: Int = 0xFFF9FAFB.toInt(), val muted: Int = 0xFF9CA3AF.toInt(), val light: Boolean = false
)

/** Menu panel themes and accent colors (inspired by the colour pickers in popular assistive-touch apps). */
object Themes {
    private fun c(v: Long) = v.toInt()

    val all = listOf(
        MenuTheme("Midnight", c(0xFF111827), c(0xFF0B0F19), c(0xFF1F2937)),
        MenuTheme("Aurora", c(0xFF2F1F6B), c(0xFF0B0F19), c(0xFF4C3D8F)),
        MenuTheme("Ocean", c(0xFF0B4A78), c(0xFF0B1220), c(0xFF1D6FA5)),
        MenuTheme("Sunset", c(0xFF5B2146), c(0xFF160F1E), c(0xFF8A3A66)),
        MenuTheme("Emerald", c(0xFF0D4B40), c(0xFF0A1517), c(0xFF16796A)),
        MenuTheme("Royal", c(0xFF2563EB), c(0xFF4F2EE8), c(0xFF6B8CF5)),
        MenuTheme("Rose", c(0xFF7A1F4A), c(0xFF1E0F1A), c(0xFFB03A74)),
        MenuTheme("Graphite", c(0xFF26262B), c(0xFF121214), c(0xFF3A3A42)),
        MenuTheme("Light", c(0xFFF8FAFC), c(0xFFE2E8F0), c(0xFFCBD5E1), c(0xFF0F172A), c(0xFF475569), true)
    )

    val accents = intArrayOf(
        c(0xFF6366F1), c(0xFF38BDF8), c(0xFF22C55E), c(0xFFF59E0B), c(0xFFEF4444), c(0xFFEC4899),
        c(0xFFA855F7), c(0xFF14B8A6), c(0xFFF97316), c(0xFF84CC16), c(0xFF64748B), c(0xFFF9FAFB)
    )

    fun theme(ctx: Context): MenuTheme = all[Prefs.theme(ctx).coerceIn(0, all.size - 1)]
    fun accent(ctx: Context): Int = accents[Prefs.accent(ctx).coerceIn(0, accents.size - 1)]

    fun withAlpha(color: Int, alphaPct: Int): Int {
        val a = (alphaPct.coerceIn(0, 100) * 255 / 100)
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}

/** Icons the user can choose for the floating orb. Keys starting with "emoji:" are drawn as text. */
object OrbIcons {
    val symbols = listOf(
        "ring" to R.drawable.ic_orb, "dot" to R.drawable.ic_dot, "cursor" to R.drawable.ic_cursor,
        "star" to R.drawable.ic_star, "heart" to R.drawable.ic_heart, "moon" to R.drawable.ic_moon,
        "cloud" to R.drawable.ic_cloud, "bulb" to R.drawable.ic_bulb, "smile" to R.drawable.ic_smile,
        "bolt" to R.drawable.ic_bolt, "check" to R.drawable.ic_check, "grid" to R.drawable.ic_apps
    )
    val emojis = listOf("😎", "🚀", "🔥", "🌸", "🐼", "🤖", "⭐", "💎")

    /** Pre-rendered 3D pictures (shown as-is, no background disc). */
    val pictures = listOf(
        "orb3d_ring" to R.drawable.orb3d_ring, "orb3d_cyan" to R.drawable.orb3d_cyan,
        "orb3d_indigo" to R.drawable.orb3d_indigo, "orb3d_rose" to R.drawable.orb3d_rose,
        "orb3d_gold" to R.drawable.orb3d_gold
    )

    fun isPicture(key: String) = pictures.any { it.first == key }
    fun res(key: String): Int =
        (pictures + symbols).firstOrNull { it.first == key }?.second ?: R.drawable.orb3d_ring
}
