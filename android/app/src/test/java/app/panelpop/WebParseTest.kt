package app.panelpop

import app.panelpop.WebParse.RawCard
import app.panelpop.WebParse.RawImage
import app.panelpop.WebParse.RawLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WebParseTest {
    @Test fun bareDomainBecomesRepositoryUrl() =
        assertEquals("https://mangadex.org/", WebParse.normalizeUrl("  MangaDex.org "))

    @Test fun textThatIsNotAWebIsRejected() {
        assertNull(WebParse.normalizeUrl("hola mundo"))
        assertNull(WebParse.normalizeUrl("localhost"))
    }

    @Test fun chapterNumberFromSpanishText() =
        assertEquals(12.0, WebParse.chapterNumber("Capítulo 12", "https://x.com/a")!!, 0.0)

    @Test fun decimalChapterFromEnglishText() =
        assertEquals(105.5, WebParse.chapterNumber("Chapter 105.5 - The end", "https://x.com/a")!!, 0.0)

    @Test fun chapterNumberFromUrlWhenTextHasNone() =
        assertEquals(33.0, WebParse.chapterNumber("Leer ahora", "https://x.com/manga/solo/capitulo-33/")!!, 0.0)

    @Test fun chaptersAreFilteredDedupedAndNewestFirst() {
        val s = "https://site.com/manga/solo-leveling/"
        val got = WebParse.chapters(s, listOf(
            RawLink("https://site.com/manga/solo-leveling/capitulo-1/", "Capítulo 1", true),
            RawLink("https://site.com/manga/solo-leveling/capitulo-3/", "Capítulo 3", true),
            RawLink("https://site.com/manga/solo-leveling/capitulo-3/#c", "Capítulo 3", true),
            RawLink("https://site.com/manga/solo-leveling/capitulo-2/", "Capítulo 2", true),
            RawLink("https://site.com/page/2/", "2", false),
            RawLink("https://otra.com/chapter-9", "Chapter 9", true),
        ))
        assertEquals(listOf(3.0, 2.0, 1.0), got.map { it.number })
    }

    @Test fun nextAndPreviousChapter() {
        val list = listOf(
            WebParse.Chapter("https://s.com/c-3", "Capítulo 3", 3.0),
            WebParse.Chapter("https://s.com/c-2", "Capítulo 2", 2.0),
            WebParse.Chapter("https://s.com/c-1", "Capítulo 1", 1.0),
        )
        val (prev, next) = WebParse.neighbors(list, "https://s.com/c-2/")
        assertEquals(1.0, prev!!.number, 0.0)
        assertEquals(3.0, next!!.number, 0.0)
    }

    @Test fun readerImagesSkipLogosAndAds() {
        val got = WebParse.pageImages(listOf(
            RawImage("https://s.com/logo.png", 200, 80, false),
            RawImage("https://cdn.s.com/ch1/01.jpg", 800, 1200, true),
            RawImage("https://cdn.s.com/ch1/02.jpg", 800, 1200, true),
            RawImage("https://cdn.s.com/ads/banner.jpg", 800, 300, true),
        ))
        assertEquals(listOf("https://cdn.s.com/ch1/01.jpg", "https://cdn.s.com/ch1/02.jpg"), got)
    }

    @Test fun seriesCardsSkipChaptersAndOtherSites() {
        val got = WebParse.seriesCards("https://s.com/", listOf(
            RawCard("https://s.com/manga/solo-leveling/", "Solo Leveling", "https://s.com/c/solo.jpg"),
            RawCard("https://s.com/manga/solo-leveling/capitulo-5/", "Cap 5", "https://s.com/c/solo.jpg"),
            RawCard("https://ads.com/x", "Anuncio", "https://ads.com/x.jpg"),
            RawCard("https://s.com/genres/accion/", "Acción", "https://s.com/g.jpg"),
        ))
        assertEquals(listOf("Solo Leveling"), got.map { it.title })
    }

    @Test fun siteNameIsRemovedFromTitle() =
        assertEquals("Solo Leveling", WebParse.cleanTitle("Solo Leveling – Manga Online | MiWeb"))

    @Test fun unreadCountsOnlyMissingChapters() {
        val list = listOf(
            WebParse.Chapter("https://s.com/c-2", "2", 2.0),
            WebParse.Chapter("https://s.com/c-1", "1", 1.0),
        )
        assertEquals(1, WebParse.unread(list, setOf(WebParse.key("https://s.com/c-1/"))))
    }

    // ManhwaWeb: los enlaces dicen "Leer - ManhwaWeb Bot 14/03/2024" y el número solo va en la dirección
    private val mwSeries = "https://manhwaweb.com/manhwa/comic-la-leyenda-de-la-hoja-del-norte_1694379618503"
    private fun mwLink(n: String) = RawLink("https://manhwaweb.com/leer/comic-la-leyenda-de-la-hoja-del-norte_1694379618503-${n}_01", "Leer - ManhwaWeb Bot 14/03/2024")

    @Test fun manhwaWebChaptersComeFromTheAddress() {
        val got = WebParse.chapters(mwSeries, listOf(mwLink("1"), mwLink("2"), mwLink("176.5"), mwLink("202"),
            RawLink("https://manhwaweb.com/library?genders=3", "Accion"),
            RawLink("https://manhwaweb.com/grupo/17746797582119", "Omniscient - Scan 2")))
        assertEquals(listOf(202.0, 176.5, 2.0, 1.0), got.map { it.number })
        assertEquals("Capítulo 176.5", got[1].name)
    }

    @Test fun manhwaWebSeriesWithOneChapterIsFound() {
        val s = "https://manhwaweb.com/manhwa/el-loco-yerno-del-gran-duque-del-norte_1793727380331"
        val got = WebParse.chapters(s, listOf(RawLink("https://manhwaweb.com/leer/el-loco-yerno-del-gran-duque-del-norte_1793727380331-1_01", "Leer - Omniscient - Scan 03/11/2026")))
        assertEquals(listOf(1.0), got.map { it.number })
    }

    @Test fun secondSeasonLinkIsNotAChapter() {
        val got = WebParse.chapters("https://s.com/manga/solo-leveling", listOf(RawLink("https://s.com/manga/solo-leveling-2", "Solo Leveling 2")))
        assertEquals(0, got.size)
    }

    @Test fun pagesInTheSameFolderCountEvenBeforeTheyLoad() {
        val dir = "https://img1mw.xyz/manhwas/hoja_169/chapter_5/ver_01"
        val got = WebParse.pageImages(listOf(
            RawImage("https://img2mw.xyz/test/music_1.jpg", 765, 520),
            RawImage("$dir/001.webp", 720, 3737), RawImage("$dir/002.webp", 720, 7475),
            RawImage("$dir/003.webp", 0, 0), RawImage("https://manhwaweb.com/assets/banners/loading.gif", 379, 402),
            RawImage("$dir/004.webp", 0, 0),
        ))
        assertEquals(listOf("$dir/001.webp", "$dir/002.webp", "$dir/003.webp", "$dir/004.webp"), got)
    }

    @Test fun uploadAndForumPagesAreNotSeries() {
        val got = WebParse.seriesCards("https://manhwaweb.com/", listOf(
            RawCard("https://uploads.manhwaweb.com/uploads", "Subir capítulo", "https://imagizer.imageshack.com/x.png"),
            RawCard("https://manhwaweb.com/foro/1789728451830ic", "aviso", "https://img2mw.xyz/test/baner_7.jpg"),
            RawCard("https://manhwaweb.com/manhwa/ao-no-flag_1793731581360", "Ao no Flag", "https://img1mw.xyz/manhwas/ao/cover.webp"),
        ))
        assertEquals(listOf("Ao no Flag"), got.map { it.title })
    }

    @Test fun chapterNamesLoseDatesAndBadges() {
        assertEquals("Capítulo 94", WebParse.chapterName("Capítulo 94 HACE 9 HORAS NUEVO 💎 90", 94.0))
        assertEquals("Capítulo 35", WebParse.chapterName("Capitulo 35 - 🎵 11/09/2026 TEMPORADA 2", 35.0))
        assertEquals("Capítulo 30", WebParse.chapterName("30 Capitulo 30 julio 12, 2026", 30.0))
        assertEquals("Capítulo 257: La Resolución Final", WebParse.chapterName("Capitulo 257 -La Resolución Final- 19 horas ago", 257.0))
    }

    @Test fun paidChaptersThatAskForLoginAreSkipped() {
        val s = "https://mhscans.com/comics/mhscans-el-inversionista-que-ve-el-futuro"
        val got = WebParse.chapters(s, listOf(
            RawLink("https://mhscans.com/auth/login?redirect=%2Fcomics%2Fx", "Capítulo 94 HACE 9 HORAS 💎", true),
            RawLink("https://mhscans.com/leer/r_iq_uFWdqSZeLAWReqNT", "Capítulo 80 GRATIS", true),
            RawLink("https://mhscans.com/leer/kAcNltf4qKEHZjcXhXVbR", "Capítulo 79 GRATIS", true),
        ))
        assertEquals(listOf(80.0, 79.0), got.map { it.number })
    }

    // DragonTranslation dibuja 30 capítulos y guarda el resto en los datos de la página
    @Test fun chaptersHiddenInPageDataAreAdded() {
        val s = "https://dragontranslation.org/manga/quiero-trabajar-tranquilamente/"
        fun ch(n: Int) = "${s}capitulo-$n/"
        val got = WebParse.chapters(s, listOf(RawLink(ch(1), "1 Capitulo 1 enero 20, 2026", true), RawLink(ch(2), "2 Capitulo 2", true)) +
            (1..46).map { RawLink(ch(it), "", fromData = true) } +
            RawLink("https://dragontranslation.org/manga/otra-serie/capitulo-9/", "", fromData = true))
        assertEquals(46, got.size)
        assertEquals(46.0, got.first().number, 0.0)
    }

    @Test fun typeLabelStuckToTitleIsRemoved() =
        assertEquals("Quiero Trabajar Tranquilamente", WebParse.cleanTitle("manhwaQuiero Trabajar Tranquilamente"))

    @Test fun catalogFallsBackToSecondNumberedPage() =
        assertEquals("https://manhwaweb.com/library?page=2", WebParse.nextListPage("https://manhwaweb.com/library", "", 1))

    @Test fun existingPageNumberIsAdvanced() =
        assertEquals("https://s.com/series?page=3", WebParse.nextListPage("https://s.com/series?page=2", "", 2))

    @Test fun declaredNextPageRejectsAdsAndLoops() {
        assertEquals("https://s.com/series?page=2", WebParse.declaredNextPage("https://s.com/series", "https://s.com/series?page=2"))
        assertNull(WebParse.declaredNextPage("https://s.com/series", "https://ads.com/page/2"))
        assertNull(WebParse.declaredNextPage("https://s.com/series", "https://s.com/series/"))
    }

    @Test fun globalSearchMatchesEveryWordWithoutAccents() {
        val raw = listOf(
            RawCard("https://s.com/manga/solo-leveling/", "Sólo Leveling", "https://s.com/covers/solo.jpg"),
            RawCard("https://s.com/manga/solo-max-level/", "Solo Max Level", "https://s.com/covers/max.jpg"),
        )
        assertEquals(listOf("Sólo Leveling"), WebParse.searchResults("https://s.com/", raw, "solo leveling").map { it.title })
    }
}
