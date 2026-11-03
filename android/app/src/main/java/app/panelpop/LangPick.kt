package app.panelpop

/**
 * Reglas puras (sin Android) para decidir en qué idioma está cada texto.
 * Se prueban en `src/test` con JUnit.
 *
 * Antes, en modo automático, cada globo se identificaba por separado con un detector que, con
 * textos cortos o mal leídos («HA HA», «ㅋㅋ»), devolvía idiomas al azar (suajili, latín…).
 * Cada suposición lanzaba la descarga de otro modelo y el traductor se quedaba esperando.
 * Ahora el idioma sale de la escritura (hangul, kana, kanji) y solo se aceptan idiomas de cómics.
 */
object LangPick {
    const val LATIN = "latin"

    /** Idiomas que la app ofrece descargar y detectar automáticamente. */
    val COMIC_LANGS = listOf("en", "ja", "ko", "zh")
    private val CJK = setOf("ja", "ko", "zh")

    /** Idiomas de alfabeto latino que se aceptan del detector; cualquier otro se trata como inglés. */
    private val LATIN_OK = setOf("en", "es", "fr", "pt", "it", "de")

    fun name(code: String): String = when (code) {
        "en" -> "inglés"; "ja" -> "japonés"; "ko" -> "coreano"; "zh" -> "chino"; "es" -> "español"
        "fr" -> "francés"; "pt" -> "portugués"; "it" -> "italiano"; "de" -> "alemán"
        else -> code
    }

    /** "ja-Latn" → "ja", "zh_Hant" → "zh", vacío → "und". */
    fun normalize(tag: String?): String {
        if (tag.isNullOrBlank()) return "und"
        return tag.trim().lowercase().substringBefore('-').substringBefore('_').ifEmpty { "und" }
    }

    private fun isHangul(c: Char) = c in '\uAC00'..'\uD7A3' || c in '\u1100'..'\u11FF' || c in '\u3130'..'\u318F'
    private fun isKana(c: Char) = c in '\u3040'..'\u30FF' || c in '\u31F0'..'\u31FF' || c in '\uFF66'..'\uFF9F'
    private fun isHan(c: Char) = c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF'
    private fun isLatin(c: Char) = c.isLetter() && c.code < 0x250

    /**
     * Escritura dominante: "ko" (hangul), "ja" (hay kana), "zh" (solo kanji/hanzi), [LATIN] o
     * null si no hay letras.
     */
    fun scriptLang(text: String): String? {
        var hangul = 0; var kana = 0; var han = 0; var latin = 0
        for (c in text) when {
            isHangul(c) -> hangul++
            isKana(c) -> kana++
            isHan(c) -> han++
            isLatin(c) -> latin++
        }
        val cjk = hangul + kana + han
        if (cjk == 0 && latin == 0) return null
        // Un carácter CJK lleva tanta información como varias letras latinas
        if (cjk * 3 < latin) return LATIN
        return when {
            hangul >= kana + han -> "ko"
            kana > 0 -> "ja"
            else -> "zh"
        }
    }

    /** Cuántos caracteres propios del idioma [lang] hay (para saber si el lector de OCR acertó). */
    fun nativeChars(text: String, lang: String): Int = when (lang) {
        "ko" -> text.count { isHangul(it) }
        "ja" -> text.count { isKana(it) || isHan(it) }
        "zh" -> text.count { isHan(it) }
        else -> text.count { isLatin(it) }
    }

    /**
     * Idioma de los textos latinos de una página a partir de las propuestas del detector
     * (etiqueta, confianza). Solo se aceptan idiomas latinos conocidos con confianza ≥ 0.5;
     * si no, inglés (el caso más común y su modelo ya viene incluido).
     */
    fun latinLang(candidates: List<Pair<String, Float>>): String {
        val best = candidates
            .map { normalize(it.first) to it.second }
            .filter { it.first in LATIN_OK && it.second >= 0.5f }
            .maxByOrNull { it.second }
        return best?.first ?: "en"
    }

    /**
     * Idioma de origen de un bloque.
     * @param script resultado de [scriptLang]
     * @param pref ajuste del usuario: auto | en | ja | ko | zh
     * @param pageLatin idioma de los textos latinos de la página ([latinLang])
     * @param pageHasKana si algún bloque de la página tiene kana (entonces los kanji son japonés)
     */
    fun resolve(script: String, pref: String, pageLatin: String, pageHasKana: Boolean): String {
        if (script == LATIN) return if (pref == "auto" || pref in CJK) pageLatin else pref
        if (pref != "auto") return pref
        return if (script == "zh" && pageHasKana) "ja" else script
    }

    /** Descarta ruido del OCR: menos de 2 letras. */
    fun worthTranslating(raw: String): Boolean = raw.length >= 2 && raw.count { it.isLetter() } >= 2
}
