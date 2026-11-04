package app.panelpop

import java.net.URI

/**
 * Reglas para convertir cualquier web de mangas en un "repositorio" (como las fuentes de Mihon).
 * La página la lee un WebView; aquí solo llegan los enlaces e imágenes en bruto y se decide qué
 * es una serie, qué es un capítulo y qué imágenes son páginas del cómic. Kotlin puro: tiene tests.
 */
object WebParse {
    data class RawLink(val url: String, val text: String, val inList: Boolean = false)
    data class RawCard(val url: String, val title: String, val image: String)
    data class RawImage(val src: String, val width: Int, val height: Int, val inReader: Boolean = false)

    data class Chapter(val url: String, val name: String, val number: Double)
    data class Card(val url: String, val title: String, val image: String)

    private val CH_WORD = Regex("""(?i)(cap[ií]tulo|chapter|chap|cap|ch|episodio|episode|ep|vol)""")
    private val CH_TEXT = Regex("""(?i)(?:cap[ií]tulo|chapter|chap|cap|ch|episodio|episode|ep)\.?\s*[-#:]?\s*(\d+(?:[.,]\d+)?)""")
    private val CH_URL = Regex("""(?i)(?:capitulo|chapter|chap|cap|ch|episode|episodio|ep)[-_/]?(\d+(?:[.-]\d+)?)""")
    private val ANY_NUM = Regex("""(\d+(?:[.,]\d+)?)""")
    private val JUNK_IMG = Regex("""(?i)(logo|avatar|icon|favicon|banner|gravatar|emoji|placeholder|loading|spinner|/ads?[/_.-]|\.svg(\?|$)|\.gif(\?|$)|discord|facebook|twitter|patreon|kofi|ko-fi)""")
    private val NOT_SERIES = Regex("""(?i)/(page|genres?|generos?|tags?|category|categoria|author|autor|artist|login|register|user|account|cuenta|search|buscar|wp-login|feed|privacy|contact|dmca)(/|$|\?)""")

    /** "mangadex.org" → "https://mangadex.org/". Null si no parece una web. */
    fun normalizeUrl(input: String): String? {
        var s = input.trim()
        if (s.isEmpty() || s.contains(' ')) return null
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
        val u = try { URI(s) } catch (e: Exception) { return null }
        val host = u.host ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        val path = if (u.rawPath.isNullOrEmpty()) "/" else u.rawPath
        val query = if (u.rawQuery != null) "?${u.rawQuery}" else ""
        return "${u.scheme.lowercase()}://${host.lowercase()}${if (u.port > 0) ":${u.port}" else ""}$path$query"
    }

    fun host(url: String): String = try { (URI(url).host ?: "").lowercase().removePrefix("www.") } catch (e: Exception) { "" }

    private fun path(url: String): String = try { URI(url).rawPath ?: "/" } catch (e: Exception) { "/" }

    /** Clave para comparar URLs: sin #fragmento ni barra final. */
    fun key(url: String): String = url.substringBefore('#').trimEnd('/').lowercase()

    fun sameSite(a: String, b: String): Boolean {
        val ha = host(a); val hb = host(b)
        if (ha.isEmpty() || hb.isEmpty()) return false
        return ha == hb || ha.endsWith(".$hb") || hb.endsWith(".$ha")
    }

    /** Número de capítulo a partir del texto del enlace o de la URL. */
    fun chapterNumber(text: String, url: String): Double? {
        CH_TEXT.find(text)?.let { return it.groupValues[1].replace(',', '.').toDoubleOrNull() }
        val p = path(url)
        CH_URL.findAll(p).lastOrNull()?.let { m ->
            val raw = m.groupValues[1]
            // "capitulo-12-5" → 12.5 ; "chapter-12" → 12
            return raw.replace('-', '.').toDoubleOrNull() ?: raw.takeWhile { it.isDigit() }.toDoubleOrNull()
        }
        val t = text.trim()
        if (t.length in 1..12) ANY_NUM.find(t)?.let { return it.groupValues[1].replace(',', '.').toDoubleOrNull() }
        return null
    }

    private fun fmt(n: Double) = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

    /** Capítulos de la página de una serie, del más nuevo al más antiguo. */
    fun chapters(seriesUrl: String, raw: List<RawLink>): List<Chapter> {
        val seriesKey = key(seriesUrl)
        data class C(val link: RawLink, val n: Double, val word: Boolean)
        var cands = raw.mapNotNull { l ->
            if (!l.url.startsWith("http") || !sameSite(l.url, seriesUrl) || key(l.url) == seriesKey) return@mapNotNull null
            val n = chapterNumber(l.text, l.url) ?: return@mapNotNull null
            C(l, n, CH_WORD.containsMatchIn(l.text) || CH_URL.containsMatchIn(path(l.url)))
        }
        val listed = cands.filter { it.inListOk() }
        cands = when {
            listed.size >= 2 -> listed
            else -> cands.filter { it.word }
        }
        // Si muchos capítulos cuelgan de la ruta de la serie (/manga/slug/capitulo-1), quédate con esos
        val sp = path(seriesUrl).trimEnd('/')
        if (sp.length > 1) {
            val under = cands.filter { path(it.link.url).startsWith("$sp/") }
            if (under.size >= 2) cands = under
        }
        val seenUrl = HashSet<String>(); val seenNum = HashSet<Double>()
        return cands.filter { seenUrl.add(key(it.link.url)) && seenNum.add(it.n) }
            .map { c ->
                val name = c.link.text.trim().replace(Regex("\\s+"), " ").take(60)
                Chapter(c.link.url, if (name.isEmpty() || name.length > 50 || !CH_WORD.containsMatchIn(name)) "Capítulo ${fmt(c.n)}" else name, c.n)
            }
            .sortedByDescending { it.number }
    }

    private fun Any.inListOk(): Boolean = this is Pair<*, *> // placeholder never used
    private val Any.dummy get() = 0

    /** Imágenes de un capítulo, en orden, sin logos ni anuncios. */
    fun pageImages(raw: List<RawImage>): List<String> {
        val clean = raw.filter { it.src.startsWith("http") && !JUNK_IMG.containsMatchIn(it.src) }
        val reader = clean.filter { it.inReader }
        val picked = if (reader.size >= 2) reader.filter { it.width == 0 || it.width >= 200 }
        else clean.filter { it.width >= 400 || it.height >= 600 }
        val seen = HashSet<String>()
        return picked.map { it.src }.filter { seen.add(key(it)) }
    }

    /** Series que se ven en la portada o en la búsqueda de un repositorio. */
    fun seriesCards(siteUrl: String, raw: List<RawCard>): List<Card> {
        val home = key(siteUrl)
        val byUrl = LinkedHashMap<String, Card>()
        for (c in raw) {
            if (!c.url.startsWith("http") || !sameSite(c.url, siteUrl)) continue
            val k = key(c.url)
            if (k == home || path(c.url).trim('/').isEmpty()) continue
            if (NOT_SERIES.containsMatchIn(path(c.url))) continue
            if (CH_URL.containsMatchIn(path(c.url).substringAfterLast('/').ifEmpty { path(c.url).trimEnd('/').substringAfterLast('/') })) continue
            if (!c.image.startsWith("http") || JUNK_IMG.containsMatchIn(c.image)) continue
            val title = cleanTitle(c.title).ifEmpty { slugTitle(c.url) }
            val prev = byUrl[k]
            if (prev == null || prev.title.length < title.length && title.length <= 80) byUrl[k] = Card(c.url, title, prev?.image ?: c.image)
        }
        return byUrl.values.toList()
    }

    /** "Solo Leveling – Leer manga online | MiWeb" → "Solo Leveling" */
    fun cleanTitle(t: String): String {
        var s = t.trim().replace(Regex("\\s+"), " ")
        for (sep in listOf(" | ", " – ", " — ", " « ", " » ")) if (s.contains(sep)) s = s.substringBefore(sep)
        s = s.replace(Regex("""(?i)^(leer|read)\s+"""), "").replace(Regex("""(?i)\s+(manga|manhwa|manhua)?\s*(online|en español|español|sub español)$"""), "")
        return s.trim()
    }

    fun slugTitle(url: String): String {
        val seg = path(url).trimEnd('/').substringAfterLast('/')
        return seg.replace(Regex("[-_]+"), " ").trim().split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /** Capítulo anterior y siguiente (lista ordenada del más nuevo al más antiguo). */
    fun neighbors(chapters: List<Chapter>, currentUrl: String): Pair<Chapter?, Chapter?> {
        val i = chapters.indexOfFirst { key(it.url) == key(currentUrl) }
        if (i < 0) return null to null
        val prev = chapters.getOrNull(i + 1)
        val next = chapters.getOrNull(i - 1)
        return prev to next
    }

    fun unread(chapters: List<Chapter>, read: Set<String>): Int = chapters.count { key(it.url) !in read }

    fun searchUrl(siteUrl: String, query: String): String {
        val base = try { URI(siteUrl).let { "${it.scheme}://${it.authority}/" } } catch (e: Exception) { siteUrl }
        return base + "?s=" + java.net.URLEncoder.encode(query.trim(), "UTF-8")
    }
}
