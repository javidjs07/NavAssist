package com.example.navassist

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView

/** Small helpers for building the premium dark UI in code (no layout XML, no libraries). */
object Ui {
    val BG = 0xFF0B0F19.toInt()
    val SURFACE = 0xFF111827.toInt()
    val BORDER = 0xFF1F2937.toInt()
    val VIOLET = 0xFF6366F1.toInt() // electric indigo (primary)
    val BLUE = 0xFF4F46E5.toInt() // deeper indigo (pressed / gradient end)
    val CYAN = 0xFF38BDF8.toInt() // secondary highlight
    val GREEN = 0xFF34D399.toInt()
    val AMBER = 0xFFFBBF24.toInt()
    val RED = 0xFFEF4444.toInt()
    val TEXT = 0xFFF9FAFB.toInt()
    val MUTED = 0xFF9CA3AF.toInt()

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun shape(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeW > 0) setStroke(strokeW, stroke)
        }

    fun oval(color: Int, stroke: Int = 0, strokeW: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokeW > 0) setStroke(strokeW, stroke)
        }

    fun grad(
        colors: IntArray,
        radius: Float,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR,
        stroke: Int = 0,
        strokeW: Int = 0
    ): GradientDrawable =
        GradientDrawable(orientation, colors).apply {
            cornerRadius = radius
            if (strokeW > 0) setStroke(strokeW, stroke)
        }

    fun ripple(content: Drawable, radius: Float, color: Int = 0x33FFFFFF): RippleDrawable {
        val mask = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = radius
        }
        return RippleDrawable(ColorStateList.valueOf(color), content, mask)
    }

    private var baseFont: Typeface? = null
    private val fonts = HashMap<Int, Typeface>()

    /** Inter (bundled in res/font), at the requested weight. Falls back to the system font. */
    fun font(c: Context, weight: Int): Typeface = fonts.getOrPut(weight) {
        val base = baseFont ?: (try {
            c.resources.getFont(R.font.inter)
        } catch (e: Exception) {
            Typeface.DEFAULT
        }).also { baseFont = it }
        Typeface.create(base, weight, false)
    }

    fun text(
        c: Context, s: CharSequence, sp: Float, color: Int,
        bold: Boolean = false, weight: Int = if (bold) 600 else 400
    ): TextView =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(color)
            typeface = font(c, weight)
        }
}
