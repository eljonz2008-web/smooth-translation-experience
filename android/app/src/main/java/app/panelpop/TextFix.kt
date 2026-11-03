package app.panelpop

/**
 * Limpieza del texto leído por OCR antes de traducirlo, y elección del color del parche.
 * Reglas puras (sin Android), probadas en `src/test`.
 */
object TextFix {
    private fun isCjk(c: Char) = c in '\u3040'..'\u30FF' || c in '\u4E00'..'\u9FFF' ||
        c in '\uAC00'..'\uD7A3' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F' || c in '\uFF66'..'\uFF9F'
    private fun isLatin(c: Char) = c.isLetter() && c.code < 0x250

    /** Une las líneas de un globo. "SECRE-" + "TO" → "SECRETO"; en CJK sin espacios. */
    fun joinLines(lines: List<String>, cjk: Boolean): String {
        if (cjk) return lines.joinToString("") { it.trim() }.trim()
        val sb = StringBuilder()
        for (l in lines.map { it.trim() }.filter { it.isNotEmpty() }) {
            if (sb.isEmpty()) { sb.append(l); continue }
            val last = sb.last()
            if (last == '-' && sb.length >= 2 && sb[sb.length - 2].isLetter() && l.first().isLetter()) {
                sb.setLength(sb.length - 1); sb.append(l)
            } else sb.append(' ').append(l)
        }
        return sb.toString()
    }

    /** Quita caracteres asiáticos sueltos que el OCR inventa en texto latino ("T ワ LIKE"). */
    fun cleanLatin(text: String): String =
        text.filter { !isCjk(it) }.replace(Regex("\\s+"), " ").trim()

    /**
     * Los cómics escriben TODO EN MAYÚSCULAS y el traductor lo entiende peor (lo trata como
     * siglas). Si casi todo está en mayúsculas, se pasa a frase normal: "I'D LIKE TO" → "I'd like to".
     */
    fun fixCaps(text: String): String {
        val letters = text.filter { isLatin(it) }
        if (letters.length < 3 || letters.count { it.isUpperCase() } < letters.length * 0.8) return text
        val low = text.lowercase()
        val sb = StringBuilder(low.length)
        var capNext = true
        for (c in low) {
            if (capNext && c.isLetter()) { sb.append(c.uppercaseChar()); capNext = false } else sb.append(c)
            if (c == '.' || c == '!' || c == '?' || c == '¡' || c == '¿') capNext = true
        }
        // el pronombre "I" en inglés (y sus contracciones: I'm, I'd, I'll)
        return sb.toString().replace(Regex("(?<![\\p{L}'])i(?=[\\s'’,.!?]|$)"), "I")
    }

    /** Texto latino listo para traducir. */
    fun prepareLatin(lines: List<String>): String = fixCaps(cleanLatin(joinLines(lines, false)))

    private fun lum(c: Int): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

    /**
     * Color del parche a partir de muestras del borde del globo (ARGB). Se separan claros y
     * oscuros y gana el grupo mayoritario; así las letras y el antialiasing no lo vuelven gris.
     * Los globos casi blancos se dejan blancos del todo.
     */
    fun pickBackground(samples: IntArray): Int {
        if (samples.isEmpty()) return WHITE
        val light = samples.filter { lum(it) >= 150 }
        val dark = samples.filter { lum(it) < 150 }
        val group = if (light.size >= dark.size) light else dark
        val r = group.sumOf { (it shr 16) and 0xFF } / group.size
        val g = group.sumOf { (it shr 8) and 0xFF } / group.size
        val b = group.sumOf { it and 0xFF } / group.size
        val avg = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        return when {
            lum(avg) >= 225 -> WHITE
            lum(avg) <= 30 -> BLACK
            else -> avg
        }
    }

    /** Puntuación de un lector de OCR: cuántos caracteres de SU escritura encontró. */
    fun readerScore(text: String, reader: String): Int = when (reader) {
        "ja" -> text.count { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' } * 3
        "ko" -> text.count { it in '\uAC00'..'\uD7A3' } * 3
        else -> text.count { isLatin(it) }
    }

    const val WHITE = -0x1          // 0xFFFFFFFF
    const val BLACK = -0x1000000    // 0xFF000000
}
