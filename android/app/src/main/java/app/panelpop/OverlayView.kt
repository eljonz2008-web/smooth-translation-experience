package app.panelpop

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View

/**
 * Un texto traducido.
 * [box] en píxeles de pantalla, [bg] = color de fondo medido alrededor del texto original.
 */
data class TranslatedBlock(val box: Rect, val text: String, val bg: Int)

/** Capa transparente, no táctil, que dibuja las traducciones sobre la app original. */
class OverlayView(ctx: Context, private val prefs: Prefs) : View(ctx) {
    @Volatile private var blocks: List<TranslatedBlock> = emptyList()
    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 106, 61)
    }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rf = RectF()

    /** Sustituye por completo lo que se ve: nunca se acumulan traducciones antiguas. */
    fun setBlocks(b: List<TranslatedBlock>) {
        blocks = b
        postInvalidate()
    }

    fun clear() = setBlocks(emptyList())

    val isEmpty: Boolean get() = blocks.isEmpty()

    override fun onDraw(c: Canvas) {
        val list = blocks
        if (list.isEmpty()) return
        val cover = prefs.coverMode
        val alpha = prefs.bgAlpha
        val offset = (prefs.offsetY * density).toInt()
        val pad = 5f * density
        val radius = (if (cover) 8f else 12f) * density

        for (b in list) {
            val r = Rect(b.box)
            r.offset(0, offset)
            if (!cover) r.offset(0, r.height() + (6 * density).toInt())

            // ancho mínimo para que el español (más largo) quepa
            val minW = (96 * density).toInt()
            if (r.width() < minW) r.inset(-(minW - r.width()) / 2, 0)
            if (r.left < 0) r.offset(-r.left, 0)
            if (r.right > width && width > 0) r.offset(width - r.right, 0)

            val textW = (r.width() - pad * 2).toInt().coerceAtLeast((40 * density).toInt())
            val boxH = (r.height() - pad * 2).coerceAtLeast(1f)

            // Colores: en modo cubrir usamos el color real del globo; en etiqueta, un chip oscuro.
            val base = if (cover) b.bg else Color.rgb(24, 22, 30)
            val light = luminance(base) > 0.55f
            bgPaint.color = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
            tp.color = if (light) Color.rgb(18, 18, 22) else Color.WHITE

            // Ajuste automático del tamaño: el mayor tamaño que quepa en la caja
            var size = ((r.height() / 2.4f).coerceIn(12 * density, 24 * density)) * prefs.fontScale
            var layout: StaticLayout
            do {
                tp.textSize = size
                layout = StaticLayout.Builder.obtain(b.text, 0, b.text.length, tp, textW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 1.05f)
                    .build()
                val fits = layout.height <= boxH
                if (!fits) size *= 0.9f
            } while (!fits && size > 9 * density)

            val h = maxOf(r.height().toFloat(), layout.height + pad * 2)
            rf.set(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.top + h)

            if (!cover) {
                rf.offset(0f, 1.5f * density)
                c.drawRoundRect(rf, radius, radius, shadowPaint)
                rf.offset(0f, -1.5f * density)
            }
            c.drawRoundRect(rf, radius, radius, bgPaint)
            if (!cover) {
                // pequeña marca de color a la izquierda para distinguir la traducción
                c.drawRoundRect(rf.left, rf.top + radius / 2, rf.left + 3f * density, rf.bottom - radius / 2,
                    2f * density, 2f * density, accentPaint)
            }
            c.save()
            c.translate(rf.left + pad, rf.top + (h - layout.height) / 2f)
            layout.draw(c)
            c.restore()
        }
    }

    private fun luminance(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
}
