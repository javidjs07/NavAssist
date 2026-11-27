package com.example.navassist

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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

    // ------------------------------------------------------------ 3D-style helpers

    fun blend(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.argb(Color.alpha(a), r, g, bl)
    }

    fun lighten(c: Int, t: Float) = blend(c, Color.WHITE, t)
    fun darken(c: Int, t: Float) = blend(c, Color.BLACK, t)

    /** Glossy raised button: light top, saturated middle, dark bottom, thin highlight edge. */
    fun button3d(c: Context, accent: Int, radiusDp: Int = 14): android.graphics.drawable.Drawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(lighten(accent, 0.30f), accent, darken(accent, 0.30f))
        )
        g.cornerRadius = dp(c, radiusDp).toFloat()
        g.setStroke(dp(c, 1), lighten(accent, 0.55f) and 0x99FFFFFF.toInt() or 0x66000000)
        return ripple(g, dp(c, radiusDp).toFloat())
    }

    /** Raised glass card. */
    fun card3d(c: Context, radiusDp: Int = 18, selectedColor: Int = 0): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1C2639.toInt(), 0xFF121A2B.toInt())
        )
        g.cornerRadius = dp(c, radiusDp).toFloat()
        if (selectedColor != 0) g.setStroke(dp(c, 2), selectedColor) else g.setStroke(dp(c, 1), 0xFF2B3647.toInt())
        return g
    }

    /** Lifts a view off the page and gives it a coloured glow. */
    fun lift(v: View, c: Context, color: Int, elevDp: Int = 8) {
        v.elevation = dp(c, elevDp).toFloat()
        v.outlineAmbientShadowColor = color
        v.outlineSpotShadowColor = color
    }

    fun menuPanelBackground(c: Context): GradientDrawable {
        val th = Themes.theme(c)
        val a = Prefs.panelOpacity(c)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Themes.withAlpha(th.top, a), Themes.withAlpha(th.bottom, a))
        ).apply {
            cornerRadius = dp(c, 24).toFloat()
            setStroke(dp(c, 1), th.stroke)
        }
    }

    fun menuChipBg(c: Context, accent: Boolean): GradientDrawable = when {
        accent -> shape(Themes.accent(c), dp(c, 16).toFloat())
        Prefs.iconTiles(c) -> shape(0x1FFFFFFF, dp(c, 16).toFloat(), 0x26FFFFFF, dp(c, 1))
        else -> shape(0x00000000, dp(c, 16).toFloat())
    }

    /** The floating orb as drawn on screen (also used for the live preview in the app). */
    fun orbView(c: Context, size: Int): FrameLayout {
        val box = FrameLayout(c)
        val key = Prefs.orbIcon(c)
        if (OrbIcons.isPicture(key)) {
            val iv = ImageView(c)
            iv.setImageResource(OrbIcons.res(key))
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            box.addView(iv, FrameLayout.LayoutParams(size, size))
            return box
        }
        val fill = Themes.withAlpha(0xFF0B0F19.toInt(), 62)
        val ring = Themes.withAlpha(Themes.accent(c), 65)
        box.background = if (Prefs.orbShape(c) == 0) oval(fill, ring, dp(c, 1))
        else shape(fill, size * 0.28f, ring, dp(c, 1))
        if (key.startsWith("emoji:")) {
            val t = TextView(c)
            t.text = key.removePrefix("emoji:")
            t.gravity = Gravity.CENTER
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.5f)
            box.addView(t, FrameLayout.LayoutParams(size, size))
        } else {
            val iv = ImageView(c)
            iv.setImageResource(OrbIcons.res(key))
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = (size * 0.25f).toInt()
            iv.setPadding(pad, pad, pad, pad)
            box.addView(iv, FrameLayout.LayoutParams(size, size))
        }
        return box
    }
}


/** Circular storage gauge with a glowing gradient arc. */
class RingView(c: Context) : View(c) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var big = ""
    var small = ""

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF1F2937.toInt() }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val tBig = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT; textAlign = Paint.Align.CENTER; typeface = Ui.font(c, 700) }
    private val tSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED; textAlign = Paint.Align.CENTER; typeface = Ui.font(c, 500) }
    private val rect = RectF()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = minOf(w, h) * 0.1f
        val pad = stroke
        rect.set(pad + stroke / 2, pad + stroke / 2, w - pad - stroke / 2, h - pad - stroke / 2)
        track.strokeWidth = stroke
        canvas.drawArc(rect, 135f, 270f, false, track)
        val sweep = 270f * progress
        if (sweep > 0.5f) {
            val shader = SweepGradient(w / 2, h / 2, intArrayOf(Ui.VIOLET, Ui.CYAN, Ui.CYAN), floatArrayOf(0f, 0.75f, 1f))
            arc.shader = shader
            glow.shader = shader
            arc.strokeWidth = stroke
            glow.strokeWidth = stroke
            canvas.save()
            canvas.rotate(135f, w / 2, h / 2)
            canvas.drawArc(rect, 0f, sweep, false, glow)
            canvas.drawArc(rect, 0f, sweep, false, arc)
            canvas.restore()
        }
        tBig.textSize = minOf(w, h) * 0.22f
        tSmall.textSize = minOf(w, h) * 0.085f
        canvas.drawText(big, w / 2, h / 2 + tBig.textSize * 0.2f, tBig)
        canvas.drawText(small, w / 2, h / 2 + tBig.textSize * 0.2f + tSmall.textSize * 1.6f, tSmall)
    }
}
