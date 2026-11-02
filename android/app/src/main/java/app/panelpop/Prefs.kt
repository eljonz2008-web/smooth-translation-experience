package app.panelpop

import android.content.Context

/** Ajustes del usuario. No se guardan capturas: solo preferencias. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("panelpop", Context.MODE_PRIVATE)

    /** auto | en | ja | ko | zh */
    var sourceLang: String
        get() = p.getString("src", "auto") ?: "auto"
        set(v) = p.edit().putString("src", v).apply()

    /** Multiplicador del tamaño de letra (0.6 – 2.0) */
    var fontScale: Float
        get() = p.getFloat("font", 1f).coerceIn(MIN_FONT, MAX_FONT)
        set(v) = p.edit().putFloat("font", v.coerceIn(MIN_FONT, MAX_FONT)).apply()

    /** Opacidad del fondo 0–255 */
    var bgAlpha: Int
        get() = p.getInt("alpha", 235).coerceIn(0, 255)
        set(v) = p.edit().putInt("alpha", v.coerceIn(0, 255)).apply()

    /** true = cubre el texto original; false = etiqueta debajo del texto */
    var coverMode: Boolean
        get() = p.getBoolean("cover", true)
        set(v) = p.edit().putBoolean("cover", v).apply()

    /** Desplazamiento vertical manual de las traducciones (dp, −50 a 50) */
    var offsetY: Int
        get() = p.getInt("offy_dp", 0).coerceIn(-50, 50)
        set(v) = p.edit().putInt("offy_dp", v.coerceIn(-50, 50)).apply()

    /** 0 = ahorro, 1 = equilibrada, 2 = alta */
    var performance: Int
        get() = p.getInt("perf", 1).coerceIn(0, 2)
        set(v) = p.edit().putInt("perf", v.coerceIn(0, 2)).apply()

    /**
     * Segundos que la pantalla debe quedarse quieta antes de leerla.
     * Mientras desplazas, las traducciones se ocultan; al parar, aparecen las nuevas.
     */
    var settleSec: Float
        get() = p.getFloat("settle", 0.6f).coerceIn(MIN_SETTLE, MAX_SETTLE)
        set(v) = p.edit().putFloat("settle", v.coerceIn(MIN_SETTLE, MAX_SETTLE)).apply()

    val settleMs: Long get() = (settleSec * 1000).toLong()
    val captureScale: Float get() = when (performance) { 0 -> 0.5f; 2 -> 0.85f; else -> 0.65f }

    companion object {
        const val MIN_FONT = 0.6f
        const val MAX_FONT = 2.0f
        const val MIN_SETTLE = 0.2f
        const val MAX_SETTLE = 3.0f
    }
}
