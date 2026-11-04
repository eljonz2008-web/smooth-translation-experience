package app.panelpop

import app.panelpop.WebParse.RawCard
import app.panelpop.WebParse.RawImage
import app.panelpop.WebParse.RawLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
