package app.panelpop

import java.io.File
import org.junit.Test

// TEMPORAL: lee volcados reales de webs y muestra lo que decide WebParse. Se borra al terminar.
class SiteProbeTest {
    @Test fun probe() {
        val dir = File("/tmp/browser/sites/tsv")
        if (!dir.isDirectory) return
        val out = StringBuilder()
        for (f in dir.listFiles()!!.sortedBy { it.name }) {
            var url = ""; var title = ""; var cover = ""
            val cards = ArrayList<WebParse.RawCard>(); val links = ArrayList<WebParse.RawLink>(); val imgs = ArrayList<WebParse.RawImage>()
            f.readLines().forEach { line ->
                val p = line.split('\t')
                when (p[0]) {
                    "U" -> url = p.getOrElse(1) { "" }
                    "T" -> title = p.getOrElse(1) { "" }
                    "V" -> cover = p.getOrElse(1) { "" }
                    "C" -> cards.add(WebParse.RawCard(p[1], p.getOrElse(2) { "" }, p.getOrElse(3) { "" }))
                    "L" -> links.add(WebParse.RawLink(p[1], p.getOrElse(2) { "" }, p.getOrElse(3) { "0" } == "1"))
                    "I" -> imgs.add(WebParse.RawImage(p[1], p[2].toInt(), p[3].toInt(), p[4] == "1"))
                }
            }
            val name = f.nameWithoutExtension
            out.append("=== $name  $url\n  title=${WebParse.cleanTitle(title)} cover=${cover.take(80)}\n")
            when {
                name.endsWith("_home") || name.endsWith("_search") -> WebParse.seriesCards(url, cards).let { s ->
                    out.append("  series=${s.size}\n"); s.take(8).forEach { out.append("   - ${it.title} | ${it.url} | ${it.image.take(60)}\n") }
                }
                name.endsWith("_series") -> WebParse.chapters(url, links).let { s ->
                    out.append("  chapters=${s.size}\n"); (s.take(5) + s.takeLast(3)).forEach { out.append("   - ${it.number} ${it.name} | ${it.url}\n") }
                }
                name.endsWith("_chap") -> WebParse.pageImages(imgs).let { s ->
                    out.append("  pages=${s.size}\n"); (s.take(3) + s.takeLast(2)).forEach { out.append("   - $it\n") }
                }
            }
        }
        File("/tmp/browser/sites/probe.txt").writeText(out.toString())
    }
}
