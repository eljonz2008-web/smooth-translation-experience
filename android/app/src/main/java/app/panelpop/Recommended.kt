package app.panelpop

/**
 * Repositorios recomendados en Explorar: webs en español ya probadas con PanelPop.
 * [search] es cómo busca cada una (comprobado a mano); las demás webs lo aprenden solas.
 */
object Recommended {
    data class Site(val name: String, val url: String, val about: String, val search: String, val adult: Boolean = false)

    val sites = listOf(
        Site("ManhwaWeb", "https://manhwaweb.com/", "Biblioteca enorme: manhwa, manga y manhua", "/library?buscar={q}", adult = true),
        Site("Olympus Scan", "https://olympusbiblioteca.com/", "Acción, fantasía, murim y regresión", WebParse.TYPE_SEARCH),
        Site("MHScans", "https://mhscans.com/", "Manhwas y manhuas de acción y sistema", "/search?q={q}"),
        Site("LectorJPG", "https://lectorjpg.com/", "Romance, BL y novelas", "/series?name={q}", adult = true),
        Site("Dragon Translation", "https://dragontranslation.org/", "Manhwas para adultos", "/?s={q}&post_type=wp-manga", adult = true),
    )

    /** Plantilla de búsqueda conocida para una web (también si redirige a otro dominio). */
    fun searchFor(url: String): String? = sites.firstOrNull { WebParse.sameSite(it.url, url) }?.search
}
