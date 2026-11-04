package app.panelpop
import org.json.JSONObject
import org.junit.Test
import java.io.File
class TmpLiveTest {
    private fun o(n: String) = JSONObject(File("/tmp/browser/scrape/$n").readText())
    @Test fun live() {
        val h = o("home.json"); val cards = ArrayList<WebParse.RawCard>()
        h.getJSONArray("cards").let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { cards.add(WebParse.RawCard(it.optString("u"), it.optString("t"), it.optString("i"))) } }
        val c = WebParse.seriesCards(h.getString("url"), cards)
        println("CARDS ${c.size}: " + c.take(8).joinToString(" | ") { it.title + " -> " + it.url })
        val s = o("series.json"); val links = ArrayList<WebParse.RawLink>()
        s.getJSONArray("links").let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { links.add(WebParse.RawLink(it.optString("u"), it.optString("t"), it.optInt("c") == 1)) } }
        val ch = WebParse.chapters(s.getString("url"), links)
        println("TITLE " + WebParse.cleanTitle(s.getString("title")) + " COVER " + s.getString("cover"))
        println("CHAPTERS ${ch.size}: first ${ch.take(3)} last ${ch.takeLast(2)}")
        val p = o("ch.json"); val imgs = ArrayList<WebParse.RawImage>()
        p.getJSONArray("images").let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { imgs.add(WebParse.RawImage(it.optString("s"), it.optInt("w"), it.optInt("h"), it.optInt("r") == 1)) } }
        val pg = WebParse.pageImages(imgs)
        println("PAGES ${pg.size}: ${pg.take(3)} ... ${pg.takeLast(1)}")
    }
}
