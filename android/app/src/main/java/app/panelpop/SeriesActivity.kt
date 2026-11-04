package app.panelpop

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** Ficha de una serie: portada, guardar en la biblioteca y lista de capítulos. */
class SeriesActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var series: Library.Series
    private lateinit var scraper: PageScraper
    private lateinit var web: WebView
    private lateinit var coverView: ImageView
    private lateinit var titleView: TextView
    private lateinit var libBtn: TextView
    private lateinit var readBtn: TextView
    private lateinit var statusView: TextView
    private lateinit var spin: ProgressBar
    private lateinit var webBtn: View
    private val adapter = ChapterAdapter()
    private var needsReload = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Library.init(this)
        ui = Ui(this)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        series = Library.obtain(url, intent.getStringExtra(EXTRA_TITLE) ?: "", intent.getStringExtra(EXTRA_COVER) ?: "")
        if (series.cover.isEmpty()) series.cover = intent.getStringExtra(EXTRA_COVER) ?: ""
        window.statusBarColor = Pal.BG; window.navigationBarColor = Pal.BG

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Pal.BG) }
        col.addView(ui.topBar("", { finish() }, ui.iconBtn("🌐") { openWeb() }).first)

        val body = FrameLayout(this)
        web = WebView(this)
        body.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val list = ListView(this).apply {
            setBackgroundColor(Pal.BG); divider = null; selector = android.graphics.drawable.ColorDrawable(0)
        }
        list.addHeaderView(header(), null, false)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, p, _ -> adapter.items.getOrNull(p - 1)?.let { read(it) } }
        list.setOnItemLongClickListener { _, _, p, _ ->
            adapter.items.getOrNull(p - 1)?.let { c ->
                val k = WebParse.key(c.url)
                if (!series.read.remove(k)) series.read.add(k)
                Library.save(); refresh()
            }; true
        }
        body.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)

        scraper = PageScraper(web, PageScraper.Want.CHAPTERS) { res -> onScraped(res) }
        // Los capítulos salen en pantalla en cuanto aparecen; el resto se va añadiendo
        scraper.onProgress = { res ->
            val chs = WebParse.chapters(res.url.ifEmpty { series.url }, res.links)
            if (chs.size > series.chapters.size || (chs.isNotEmpty() && !freshList)) {
                series.chapters = chs; freshList = true
                statusView.text = "${chs.size} capítulos · buscando más…"
                refresh()
            }
        }
        refresh()
        load()
    }

    private fun header(): View {
        val h = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(8)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        coverView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = ui.round(Pal.CARD, 12); clipToOutline = true
        }
        top.addView(coverView, LinearLayout.LayoutParams(ui.dp(118), ui.dp(177)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(4), 0, 0) }
        titleView = ui.text("", 21f, Pal.TEXT, true).apply { maxLines = 5; ellipsize = TextUtils.TruncateAt.END }
        info.addView(titleView)
        info.addView(ui.text(series.source, 13f, Pal.MUTED), ui.lp(top = 6))
        top.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        h.addView(top)

        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        libBtn = ui.button("", false) {
            Library.setInLibrary(series, !series.inLibrary)
            Toast.makeText(this, if (series.inLibrary) "Añadida a la biblioteca" else "Quitada de la biblioteca", Toast.LENGTH_SHORT).show()
            refresh()
        }
        readBtn = ui.button("Empezar") { continueReading() }
        btns.addView(libBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btns.addView(readBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(10) })
        h.addView(btns, ui.lp(top = 18))

        val st = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        spin = ProgressBar(this).apply { isIndeterminate = true; indeterminateTintList = android.content.res.ColorStateList.valueOf(Pal.ACCENT) }
        st.addView(spin, LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)).apply { rightMargin = ui.dp(10) })
        statusView = ui.text("", 14f, Pal.MUTED, true)
        st.addView(statusView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        h.addView(st, ui.lp(top = 22))
        webBtn = ui.button("Abrir la web", false) { openWeb() }
        h.addView(webBtn, ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, top = 10))
        return h
    }

    override fun onResume() {
        super.onResume()
        if (needsReload) { needsReload = false; load() }
        refresh()
    }

    override fun onDestroy() { scraper.cancel(); web.destroy(); super.onDestroy() }

    private var freshList = false

    private fun load() {
        spin.visibility = View.VISIBLE; webBtn.visibility = View.GONE; freshList = false
        statusView.text = if (series.chapters.isEmpty()) "Buscando capítulos…" else "${series.chapters.size} capítulos · actualizando…"
        scraper.load(series.url)
    }

    private fun onScraped(res: Scraped?) {
        spin.visibility = View.GONE
        if (res != null) {
            val t = WebParse.cleanTitle(res.title)
            if (t.isNotEmpty() && t.length <= 120) series.title = t
            // Algunas webs ponen como "imagen" la propia página o su logo: entonces se queda la portada de la cuadrícula
            if (res.cover.startsWith("http") && WebParse.key(res.cover) != WebParse.key(res.url) && WebParse.key(res.cover) != WebParse.key(series.url) &&
                (series.cover.isEmpty() || !WebParse.looksLikeLogo(res.cover))) series.cover = res.cover
            val chs = WebParse.chapters(res.url.ifEmpty { series.url }, res.links)
            if (chs.isNotEmpty()) series.chapters = chs
            if (series.inLibrary || series.read.isNotEmpty()) Library.save()
        }
        refresh()
    }

    private fun refresh() {
        titleView.text = series.title
        ImageLoader.load(coverView, series.cover, series.url)
        libBtn.text = if (series.inLibrary) "♥  En biblioteca" else "♡  Añadir"
        libBtn.setTextColor(if (series.inLibrary) Pal.ACCENT else Pal.TEXT)
        val n = series.chapters.size
        if (spin.visibility != View.VISIBLE) {
            statusView.text = if (n == 0) "No encontré capítulos. Ábrela en la web: si pide verificación, resuélvela y vuelve." else "$n capítulos"
            webBtn.visibility = if (n == 0) View.VISIBLE else View.GONE
        }
        val next = nextToRead()
        readBtn.text = when {
            next == null -> "Empezar"
            series.read.isEmpty() -> "▶  Empezar"
            else -> "▶  ${next.name}"
        }
        readBtn.alpha = if (n == 0) 0.5f else 1f
        adapter.items = series.chapters; adapter.notifyDataSetChanged()
    }

    /** El primer capítulo sin leer después del último leído (o el primero de todos). */
    private fun nextToRead(): WebParse.Chapter? {
        val chs = series.chapters
        if (chs.isEmpty()) return null
        val last = series.lastRead?.let { l -> chs.indexOfFirst { WebParse.key(it.url) == WebParse.key(l) } } ?: -1
        if (last >= 0) {
            val read = WebParse.key(chs[last].url) in series.read
            return if (read && last > 0) chs[last - 1] else chs[last]
        }
        return chs.last()
    }

    private fun continueReading() { nextToRead()?.let { read(it) } ?: Toast.makeText(this, "Aún no hay capítulos", Toast.LENGTH_SHORT).show() }

    private fun read(c: WebParse.Chapter) = startActivity(Intent(this, ReaderActivity::class.java)
        .putExtra(ReaderActivity.EXTRA_SERIES, series.url).putExtra(ReaderActivity.EXTRA_CHAPTER, c.url))

    private fun openWeb() {
        needsReload = true
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, series.url))
    }

    private inner class ChapterAdapter : BaseAdapter() {
        var items: List<WebParse.Chapter> = emptyList()
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val row = (convert as? LinearLayout) ?: LinearLayout(this@SeriesActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14))
                addView(ui.text("", 15f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.text("", 12f, Pal.MUTED))
            }
            val c = items[p]
            val read = WebParse.key(c.url) in series.read
            val last = series.lastRead?.let { WebParse.key(it) == WebParse.key(c.url) } == true
            (row.getChildAt(0) as TextView).apply {
                text = c.name; setTextColor(if (read) Pal.MUTED else Pal.TEXT)
                typeface = Typeface.create(if (read) "sans-serif" else "sans-serif-medium", Typeface.NORMAL)
            }
            (row.getChildAt(1) as TextView).text = when { last -> "Último leído"; read -> "Leído"; else -> "" }
            return row
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COVER = "cover"
    }
}
