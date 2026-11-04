package app.panelpop

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.PathParser

/** Paleta y piezas de interfaz compartidas por todas las pantallas (sin XML). Estilo Mihon: azul noche. */
object Pal {
    val BG = rgb(0x011C3A)
    val CARD = rgb(0x05284A)
    val CARD_HI = rgb(0x0C3358)
    val LINE = rgb(0x1B3F63)
    val TEXT = rgb(0xDCE6F5)
    val MUTED = rgb(0x7F93AD)
    val DIM = rgb(0x4B6684)
    /** Azul claro para iconos activos, puntos y textos destacados. */
    val ACCENT = rgb(0x5ED4FC)
    val ACCENT2 = rgb(0x3FA9D6)
    /** Contenedores de botones principales (como «Reanudar» en Mihon) y su texto. */
    val CONT = rgb(0x004D61)
    val CONT2 = rgb(0x00677F)
    val ON_CONT = rgb(0xB8EAFF)
    val ON_ACCENT = rgb(0x00344A)
    val OK = rgb(0x4ADE80)

    private fun rgb(v: Int) = (0xFF shl 24) or v
}

/** Iconos Material dibujados desde su trazo (24×24), sin archivos de imagen. */
object Icons {
    const val BACK = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
    const val HEART = "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"
    const val HEART_LINE = "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5 22,5.42 19.58,3 16.5,3zM12.1,18.55l-0.1,0.1 -0.1,-0.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,0.99 3.57,2.36h1.87C13.46,5.99 14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z"
    const val GLOBE = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1L8,12v-2h2c0.55,0 1,-0.45 1,-1L11,7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z"
    const val PLAY = "M8,5v14l11,-7z"
    const val CHECK_CIRCLE = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"
    const val CIRCLE_LINE = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z"
    const val REFRESH = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
    const val SHARE = "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
    const val SEARCH = "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
    const val ADD = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
    const val LIBRARY = "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,12l-2.5,-1.5L15,12V4h5v8z"
    const val EXPLORE = "M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1c0.61,0 1.1,-0.49 1.1,-1.1s-0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z"
    const val TRANSLATE = "M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53L17,6L17,4h-7L10,2L8,2v2L1,4v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z"
    const val EXPAND = "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z"
    const val COLLAPSE = "M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z"
    const val CHEVRON = "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z"
    const val CLOCK = "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z"
    const val STAR = "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"
    const val BOOK = "M18,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,4h5v8l-2.5,-1.5L6,12V4z"
    const val CHECK = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"

    private val cache = HashMap<String, Path>()
    fun path(data: String): Path = cache.getOrPut(data) { PathParser.createPathFromPathData(data) }
}

/** Dibuja un icono de [Icons] a cualquier tamaño y color. */
class IconDrawable(data: String, color: Int) : Drawable() {
    private val src = Icons.path(data)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val scaled = Path()
    private val m = Matrix()
    fun setColor(c: Int) { p.color = c; invalidateSelf() }
    override fun draw(c: Canvas) {
        val b = bounds
        m.setScale(b.width() / 24f, b.height() / 24f); m.postTranslate(b.left.toFloat(), b.top.toFloat())
        src.transform(m, scaled); c.drawPath(scaled, p)
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class Ui(val ctx: Context) {
    val d = ctx.resources.displayMetrics.density
    fun dp(v: Int) = (v * d).toInt()
    fun dpf(v: Float) = v * d

    fun round(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(color)
        if (stroke != null) setStroke(dp(1), stroke)
    }

    /** Fondo de los botones principales (texto en [Pal.ON_CONT]). */
    fun gradient(r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Pal.CONT2, Pal.CONT))
        .apply { cornerRadius = dp(r).toFloat() }

    fun text(t: String, size: Float, color: Int = Pal.TEXT, bold: Boolean = false) = TextView(ctx).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    fun flp(w: Int, h: Int, gravity: Int) = FrameLayout.LayoutParams(w, h, gravity)

    fun icon(data: String, color: Int = Pal.TEXT) = ImageView(ctx).apply { setImageDrawable(IconDrawable(data, color)) }

    /** Botón relleno (principal) o tonal con borde (secundario). */
    fun button(t: String, primary: Boolean = true, onClick: () -> Unit) = text(t, 14f, if (primary) Pal.ON_CONT else Pal.TEXT, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(11), dp(18), dp(11))
        background = if (primary) gradient(22) else round(Pal.CARD_HI, 22, Pal.LINE)
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    /** Icono de texto redondo para barras (ES…). */
    fun iconBtn(t: String, onClick: () -> Unit) = text(t, 16f, Pal.TEXT, true).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        background = round(Pal.CARD_HI, 22)
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** Botón de barra con icono dibujado, sin fondo (como en Mihon). */
    fun iconAction(data: String, color: Int = Pal.TEXT, onClick: () -> Unit) = ImageView(ctx).apply {
        setImageDrawable(IconDrawable(data, color))
        setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Pal.LINE), null, round(Pal.CARD_HI, 22))
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** Barra superior: ← Título  [acciones] */
    fun topBar(title: String, onBack: (() -> Unit)?, vararg actions: View, bg: Int = Pal.BG): Pair<LinearLayout, TextView> {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(bg)
        }
        if (onBack != null) bar.addView(iconAction(Icons.BACK) { onBack() })
        val tv = text(title, 21f, Pal.TEXT, false).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(if (onBack != null) 8 else 12), 0, dp(8), 0)
        }
        bar.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        for (a in actions) bar.addView(a, LinearLayout.LayoutParams(a.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            a.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(4) })
        return bar to tv
    }

    /** Título de sección pequeño, en mayúsculas. */
    fun section(t: String) = text(t, 13f, Pal.ACCENT, true).apply { letterSpacing = 0.04f }

    fun empty(title: String, sub: String): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(32), dp(32), dp(32), dp(32))
        addView(text(title, 18f, Pal.TEXT, true).apply { gravity = Gravity.CENTER })
        addView(text(sub, 14f, Pal.MUTED).apply { gravity = Gravity.CENTER }, lp(top = 8))
    }
}
