package app.panelpop

import android.app.Activity
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** Ficha de una serie como en Mihon: portada sobre fondo difuminado, acciones, sinopsis, géneros y capítulos. */
class SeriesActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var series: Library.Series
    private lateinit var scraper: PageScraper
    private lateinit var web: WebView
    private lateinit var list: ListView
    private lateinit var backdrop: ImageView
    private lateinit var coverView: ImageView
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var libIcon: IconDrawable
    private lateinit var libLabel: TextView
    private lateinit var descView: TextView
    private lateinit var descMore: ImageView
    private lateinit var genreRow: LinearLayout
    private lateinit var genreScroll: View
    private lateinit var countView: TextView
    private lateinit var statusView: TextView
    private lateinit var spin: ProgressBar
    private lateinit var webBtn: View
    private lateinit var fab: LinearLayout
    private lateinit var fabText: TextView
    private lateinit var bar: LinearLayout
    private lateinit var barTitle: TextView
    private val adapter = ChapterAdapter()
    private var needsReload = false
    private var descOpen = false
    private var freshList = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Library.init(this)
        ui = Ui(this)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        series = Library.obtain(url, intent.getStringExtra(EXTRA_TITLE) ?: "", intent.getStringExtra(EXTRA_COVER) ?: "")
        if (series.cover.isEmpty()) series.cover = intent.getStringExtra(EXTRA_COVER) ?: ""
        window.statusBarColor = Pal.BG; window.navigationBarColor = Pal.BG

        val root = FrameLayout(this).apply { setBackgroundColor(Pal.BG) }
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(1, 1))

        list = ListView(this).apply {
            setBackgroundColor(Pal.BG); divider = null; selector = android.graphics.drawable.ColorDrawable(0)
            clipToPadding = false; setPadding(0, 0, 0, ui.dp(104))
        }
        list.addHeaderView(header(), null, false)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, p, _ -> adapter.items.getOrNull(p - 1)?.let { read(it) } }
        list.setOnItemLongClickListener { _, _, p, _ -> adapter.items.getOrNull(p - 1)?.let { toggleRead(it) }; true }
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView, st: Int) {}
            override fun onScroll(v: AbsListView, first: Int, visible: Int, total: Int) {
                val top = v.getChildAt(0)?.top ?: 0
                val solid = first > 0 || top < -ui.dp(150)
                bar.setBackgroundColor(if (solid) Pal.BG else 0)
                barTitle.visibility = if (solid) View.VISIBLE else View.INVISIBLE
            }
        })
        root.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Barra superior transparente encima del fondo (se vuelve sólida al bajar)
        val (b, t) = ui.topBar("", { finish() },
            ui.iconAction(Icons.REFRESH) { load() },
            ui.iconAction(Icons.GLOBE) { openWeb() }, bg = 0)
        bar = b; barTitle = t; barTitle.visibility = View.INVISIBLE; barTitle.textSize = 18f
        root.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Botón flotante «Reanudar», como en Mihon
        fab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(20), ui.dp(16), ui.dp(24), ui.dp(16))
            background = ui.round(Pal.CONT, 18); elevation = ui.dpf(6f)
            isClickable = true; setOnClickListener { continueReading() }
        }
        fab.addView(ui.icon(Icons.PLAY, Pal.ON_CONT), LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
        fabText = ui.text("Empezar", 16f, Pal.ON_CONT, true).apply { setPadding(ui.dp(12), 0, 0, 0) }
        fab.addView(fabText)
        root.addView(fab, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
            .apply { setMargins(0, 0, ui.dp(16), ui.dp(20)) })
        setContentView(root)

        scraper = PageScraper(web, PageScraper.Want.CHAPTERS) { res -> onScraped(res) }
        // Los capítulos salen en pantalla en cuanto aparecen; el resto se va añadiendo
        scraper.onProgress = { res ->
            val chs = WebParse.chapters(res.url.ifEmpty { series.url }, res.links)
            if (chs.size > series.chapters.size || (chs.isNotEmpty() && !freshList)) {
                series.chapters = chs; freshList = true
                statusView.text = "Buscando más…"
                applyInfo(res, save = false)
                refresh()
            }
        }
        refresh()
        load()
    }

    private fun header(): View {
        val box = FrameLayout(this)
        backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.35f
            if (Build.VERSION.SDK_INT >= 31) setRenderEffect(RenderEffect.createBlurEffect(ui.dpf(18f), ui.dpf(18f), Shader.TileMode.CLAMP))
        }
        box.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(330)))
        box.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x66011C3A, 0x99011C3A.toInt(), Pal.BG))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(330)))

        val h = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(68), ui.dp(16), 0) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        coverView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = ui.round(Pal.CARD, 8); clipToOutline = true; elevation = ui.dpf(4f)
        }
        top.addView(coverView, LinearLayout.LayoutParams(ui.dp(108), ui.dp(162)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), 0, 0, 0) }
        titleView = ui.text("", 21f, Pal.TEXT).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.05f) }
        info.addView(titleView)
        info.addView(metaRow(Icons.GLOBE, series.source), ui.lp(top = 8))
        metaView = ui.text("", 14f, Pal.MUTED)
        info.addView(metaRow(Icons.CLOCK, null, metaView), ui.lp(top = 4))
        top.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        h.addView(top)

        // Acciones: biblioteca · actualizar · compartir · web
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        libIcon = IconDrawable(Icons.HEART_LINE, Pal.MUTED)
        libLabel = ui.text("Añadir", 12.5f, Pal.MUTED)
        actions.addView(action(libIcon, libLabel) {
            Library.setInLibrary(series, !series.inLibrary)
            Toast.makeText(this, if (series.inLibrary) "Añadida a la biblioteca" else "Quitada de la biblioteca", Toast.LENGTH_SHORT).show()
            refresh()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(action(IconDrawable(Icons.REFRESH, Pal.MUTED), ui.text("Actualizar", 12.5f, Pal.MUTED)) { load() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(action(IconDrawable(Icons.SHARE, Pal.MUTED), ui.text("Compartir", 12.5f, Pal.MUTED)) { share() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(action(IconDrawable(Icons.GLOBE, Pal.MUTED), ui.text("WebView", 12.5f, Pal.MUTED)) { openWeb() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        h.addView(actions, ui.lp(top = 18))

        // Sinopsis (toca para ver toda)
        val descBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isClickable = true; setOnClickListener { descOpen = !descOpen; refresh() } }
        descView = ui.text("", 14.5f, Pal.TEXT).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END; alpha = 0.9f }
        descBox.addView(descView)
        descMore = ImageView(this).apply { setImageDrawable(IconDrawable(Icons.EXPAND, Pal.TEXT)) }
        descBox.addView(descMore, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        h.addView(descBox, ui.lp(top = 18))

        genreRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        genreScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(genreRow) }
        h.addView(genreScroll, ui.lp(top = 6))

        // Cabecera de capítulos
        val chHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        countView = ui.text("", 17f, Pal.TEXT)
        chHead.addView(countView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        spin = ProgressBar(this).apply { isIndeterminate = true; indeterminateTintList = android.content.res.ColorStateList.valueOf(Pal.ACCENT) }
        chHead.addView(spin, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply { rightMargin = ui.dp(8) })
        statusView = ui.text("", 13f, Pal.MUTED)
        chHead.addView(statusView)
        h.addView(chHead, ui.lp(top = 22))
        webBtn = ui.button("Abrir la web", false) { openWeb() }
        h.addView(webBtn, ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, top = 12))
        h.addView(View(this), ui.lp(h = ui.dp(6)))
        box.addView(h)
        return box
    }

    private fun metaRow(icon: String, text: String?, tv: TextView? = null): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(ui.icon(icon, Pal.MUTED), LinearLayout.LayoutParams(ui.dp(17), ui.dp(17)))
        val t = tv ?: ui.text(text ?: "", 14f, Pal.MUTED)
        t.maxLines = 2; t.ellipsize = TextUtils.TruncateAt.END
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(8) })
        return row
    }

    private fun action(icon: IconDrawable, label: TextView, onClick: () -> Unit): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, ui.dp(6), 0, ui.dp(6))
            isClickable = true; setOnClickListener { onClick() }
            background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Pal.LINE), null, ui.round(Pal.CARD, 12))
        }
        v.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)))
        label.gravity = Gravity.CENTER
        v.addView(label, ui.lp(top = 6))
        return v
    }

    private fun chip(t: String) = ui.text(t, 13f, Pal.TEXT).apply {
        setPadding(ui.dp(14), ui.dp(7), ui.dp(14), ui.dp(7)); background = ui.round(0, 8, Pal.LINE)
    }

    override fun onResume() {
        super.onResume()
        if (needsReload) { needsReload = false; load() }
        refresh()
    }

    override fun onDestroy() { scraper.cancel(); web.destroy(); super.onDestroy() }

    private fun load() {
        spin.visibility = View.VISIBLE; webBtn.visibility = View.GONE; freshList = false
        statusView.text = if (series.chapters.isEmpty()) "Buscando capítulos…" else "Actualizando…"
        scraper.load(series.url)
    }

    private fun applyInfo(res: Scraped, save: Boolean) {
        val t = WebParse.cleanTitle(res.title)
        if (t.isNotEmpty() && t.length <= 120) series.title = t
        // Algunas webs ponen como "imagen" la propia página o su logo: entonces se queda la portada de la cuadrícula
        if (res.cover.startsWith("http") && WebParse.key(res.cover) != WebParse.key(res.url) && WebParse.key(res.cover) != WebParse.key(series.url) &&
            (series.cover.isEmpty() || !WebParse.looksLikeLogo(res.cover))) series.cover = res.cover
        WebParse.cleanDescription(res.desc).let { if (it.isNotEmpty()) series.desc = it }
        WebParse.genres(res.genres).let { if (it.isNotEmpty()) series.genres = it }
        if (save && (series.inLibrary || series.read.isNotEmpty())) Library.save()
    }

    private fun onScraped(res: Scraped?) {
        spin.visibility = View.GONE; statusView.text = ""
        if (res != null) {
            val chs = WebParse.chapters(res.url.ifEmpty { series.url }, res.links)
            if (chs.isNotEmpty()) series.chapters = chs
            applyInfo(res, save = true)
        }
        refresh()
    }

    private fun refresh() {
        titleView.text = series.title; barTitle.text = series.title
        ImageLoader.load(coverView, series.cover, series.url)
        ImageLoader.load(backdrop, series.cover, series.url)
        val n = series.chapters.size
        metaView.text = listOfNotNull(
            series.chapters.firstOrNull()?.date?.ifEmpty { null }?.let { "Actualizado: $it" },
            if (n > 0) "$n capítulos" else null,
        ).joinToString(" • ").ifEmpty { "Cargando…" }

        libIcon.setColor(if (series.inLibrary) Pal.ACCENT else Pal.MUTED)
        libIcon.let { (it as IconDrawable) }
        libLabel.text = if (series.inLibrary) "En biblioteca" else "Añadir"
        libLabel.setTextColor(if (series.inLibrary) Pal.ACCENT else Pal.MUTED)
        (libLabel.parent as? LinearLayout)?.getChildAt(0)?.let { (it as ImageView).setImageDrawable(IconDrawable(if (series.inLibrary) Icons.HEART else Icons.HEART_LINE, if (series.inLibrary) Pal.ACCENT else Pal.MUTED)) }

        descView.text = series.desc
        (descView.parent as View).visibility = if (series.desc.isEmpty()) View.GONE else View.VISIBLE
        descView.maxLines = if (descOpen) 200 else 3
        descMore.setImageDrawable(IconDrawable(if (descOpen) Icons.COLLAPSE else Icons.EXPAND, Pal.TEXT))
        genreRow.removeAllViews()
        series.genres.forEachIndexed { i, g -> genreRow.addView(chip(g), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) leftMargin = ui.dp(8) }) }
        genreScroll.visibility = if (series.genres.isEmpty()) View.GONE else View.VISIBLE

        countView.text = if (n == 0) "Capítulos" else "$n capítulos"
        if (spin.visibility != View.VISIBLE) {
            statusView.text = ""
            if (n == 0) countView.text = "No encontré capítulos. Ábrela en la web: si pide verificación, resuélvela y vuelve."
            webBtn.visibility = if (n == 0) View.VISIBLE else View.GONE
        }
        val next = nextToRead()
        fabText.text = when {
            next == null -> "Empezar"
            series.read.isEmpty() -> "Empezar"
            else -> "Reanudar"
        }
        fab.visibility = if (n == 0) View.GONE else View.VISIBLE
        adapter.items = series.chapters; adapter.notifyDataSetChanged()
    }

    private fun toggleRead(c: WebParse.Chapter) {
        val k = WebParse.key(c.url)
        if (!series.read.remove(k)) series.read.add(k)
        Library.save(); refresh()
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

    private fun share() = startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "${series.title}\n${series.url}"), "Compartir"))

    private fun openWeb() {
        needsReload = true
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, series.url))
    }

    private inner class ChapterAdapter : BaseAdapter() {
        var items: List<WebParse.Chapter> = emptyList()
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()

        private inner class Row(val dot: View, val title: TextView, val sub: TextView, val mark: ImageView)

        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val v = convert ?: build()
            val r = v.tag as Row
            val c = items[p]
            val read = WebParse.key(c.url) in series.read
            val last = series.lastRead?.let { WebParse.key(it) == WebParse.key(c.url) } == true
            r.title.text = c.name
            r.title.setTextColor(if (read) Pal.DIM else Pal.TEXT)
            r.dot.visibility = if (read) View.GONE else View.VISIBLE
            r.sub.text = listOfNotNull(c.date.ifEmpty { null }, if (last) "Último leído" else null).joinToString(" • ")
            r.sub.visibility = if (r.sub.text.isEmpty()) View.GONE else View.VISIBLE
            r.sub.setTextColor(if (read) Pal.DIM else Pal.MUTED)
            r.mark.setImageDrawable(IconDrawable(if (read) Icons.CHECK_CIRCLE else Icons.CIRCLE_LINE, if (read) Pal.MUTED else Pal.DIM))
            r.mark.setOnClickListener { toggleRead(c) }
            return v
        }

        private fun build(): View {
            val row = LinearLayout(this@SeriesActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(16), ui.dp(12), ui.dp(8), ui.dp(12))
            }
            val textCol = LinearLayout(this@SeriesActivity).apply { orientation = LinearLayout.VERTICAL }
            val line = LinearLayout(this@SeriesActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val dot = View(this@SeriesActivity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Pal.ACCENT) } }
            line.addView(dot, LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)).apply { rightMargin = ui.dp(10) })
            val title = ui.text("", 15.5f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            line.addView(title)
            textCol.addView(line)
            val sub = ui.text("", 13f, Pal.MUTED)
            textCol.addView(sub, ui.lp(top = 4))
            row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val mark = ImageView(this@SeriesActivity).apply { setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10)); isClickable = true }
            row.addView(mark, LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)))
            row.tag = Row(dot, title, sub, mark)
            return row
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COVER = "cover"
    }
}
