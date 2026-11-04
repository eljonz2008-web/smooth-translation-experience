package app.panelpop

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Paleta y piezas de interfaz compartidas por todas las pantallas (sin XML). */
object Pal {
    val BG = Color.rgb(15, 13, 20)
    val CARD = Color.rgb(27, 24, 34)
    val CARD_HI = Color.rgb(38, 34, 48)
    val LINE = Color.rgb(48, 44, 60)
    val TEXT = Color.rgb(246, 242, 236)
    val MUTED = Color.rgb(160, 154, 176)
    val ACCENT = Color.rgb(255, 106, 61)
    val ACCENT2 = Color.rgb(255, 77, 109)
    val OK = Color.rgb(74, 222, 128)
}

class Ui(val ctx: Context) {
    val d = ctx.resources.displayMetrics.density
    fun dp(v: Int) = (v * d).toInt()
    fun dpf(v: Float) = v * d

    fun round(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(color)
        if (stroke != null) setStroke(dp(1), stroke)
    }

    fun gradient(r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Pal.ACCENT, Pal.ACCENT2))
        .apply { cornerRadius = dp(r).toFloat() }

    fun text(t: String, size: Float, color: Int = Pal.TEXT, bold: Boolean = false) = TextView(ctx).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-black", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    fun flp(w: Int, h: Int, gravity: Int) = FrameLayout.LayoutParams(w, h, gravity)

    /** Botón con degradado (principal) o con borde (secundario). */
    fun button(t: String, primary: Boolean = true, onClick: () -> Unit) = text(t, 14f, if (primary) Color.WHITE else Pal.TEXT, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(11), dp(16), dp(11))
        background = if (primary) gradient(14) else round(Pal.CARD_HI, 14, Pal.LINE)
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    /** Icono de texto redondo para barras (←, ⌕, ES…). */
    fun iconBtn(t: String, onClick: () -> Unit) = text(t, 18f, Pal.TEXT, true).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        background = round(Pal.CARD_HI, 22)
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** Barra superior: ← Título  [acciones] */
    fun topBar(title: String, onBack: (() -> Unit)?, vararg actions: View): Pair<LinearLayout, TextView> {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Pal.BG)
        }
        if (onBack != null) bar.addView(iconBtn("←", onBack))
        val tv = text(title, 19f, Pal.TEXT, true).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(if (onBack != null) 12 else 8), 0, dp(8), 0)
        }
        bar.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        for (a in actions) bar.addView(a, LinearLayout.LayoutParams(a.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            a.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        return bar to tv
    }

    fun empty(title: String, sub: String): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(32), dp(32), dp(32), dp(32))
        addView(text(title, 18f, Pal.TEXT, true).apply { gravity = Gravity.CENTER })
        addView(text(sub, 14f, Pal.MUTED).apply { gravity = Gravity.CENTER }, lp(top = 8))
    }
}
