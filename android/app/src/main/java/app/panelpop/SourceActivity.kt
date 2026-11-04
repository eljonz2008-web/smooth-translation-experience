package app.panelpop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast

/** Un repositorio (una web): sus series en cuadrícula y búsqueda, como una fuente de Mihon. */
class SourceActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var scraper: PageScraper
    private lateinit var web: WebView
    private lateinit var adapter: CoverAdapter
    private lateinit var status: LinearLayout
    private lateinit var statusText: android.widget.TextView
    private lateinit var spin: ProgressBar
    private lateinit var openBtn: View
    private lateinit var site: String
    private var current = ""

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Library.init(this)
        ui = Ui(this)
        site = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME) ?: WebParse.host(site)
        window.statusBarColor = Pal.BG; window.navigationBarColor = Pal.BG

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Pal.BG) }
        val (bar, _) = ui.topBar(name, { finish() }, ui.iconBtn("🌐") { openWeb(current.ifEmpty { site }) })
        col.addView(bar)

        // Búsqueda
        val search = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(10))
        }
        val input = EditText(this).apply {
            hint = "Buscar en $name"; setHintTextColor(Pal.MUTED); setTextColor(Pal.TEXT); textSize = 15f
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = ui.round(Pal.CARD, 14, Pal.LINE); setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
        }
        fun go() {
            val q = input.text.toString().trim()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
            load(if (q.isEmpty()) site else WebParse.searchUrl(site, q))
        }
        input.setOnEditorActionListener { _, a, e -> if (a == EditorInfo.IME_ACTION_SEARCH || e?.keyCode == KeyEvent.KEYCODE_ENTER) { go(); true } else false }
        search.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        search.addView(ui.button("Buscar") { go() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(8) })
        col.addView(search)

        // Contenido: WebView (detrás, lee la página) + cuadrícula + estado
        val body = FrameLayout(this)
        web = WebView(this)
        body.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val cover = FrameLayout(this).apply { setBackgroundColor(Pal.BG) }
        adapter = CoverAdapter(ui, emptyList())
        cover.addView(ui.coverGrid(adapter, { openSeries(it) }, { quickAdd(it) }))
        status = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32)) }
        spin = ProgressBar(this).apply { isIndeterminate = true; indeterminateTintList = android.content.res.ColorStateList.valueOf(Pal.ACCENT) }
        statusText = ui.text("", 15f, Pal.MUTED).apply { gravity = Gravity.CENTER }
        openBtn = ui.button("Abrir la web") { openWeb(current.ifEmpty { site }) }
        status.addView(spin); status.addView(statusText, ui.lp(top = 12)); status.addView(openBtn, ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, top = 16))
        cover.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        body.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)

        scraper = PageScraper(web, PageScraper.Want.CARDS) { res -> show(res) }
        load(site)
    }

    private var needsReload = false

    override fun onResume() {
        super.onResume()
        // Al volver de la web (p. ej. tras pasar una verificación) se vuelve a intentar
        if (needsReload) { needsReload = false; load(current.ifEmpty { site }) }
    }

    override fun onDestroy() { scraper.cancel(); web.destroy(); super.onDestroy() }

    private fun load(url: String) {
        current = url
        adapter.items = emptyList(); adapter.notifyDataSetChanged()
        status.visibility = View.VISIBLE; spin.visibility = View.VISIBLE; openBtn.visibility = View.GONE
        statusText.text = "Cargando series…"
        scraper.load(url)
    }

    private fun show(res: Scraped?) {
        val cards = res?.let { WebParse.seriesCards(site, it.cards) } ?: emptyList()
        adapter.items = cards.map { CoverItem(it.url, it.title, it.image, res?.url ?: site) }
        adapter.notifyDataSetChanged()
        spin.visibility = View.GONE
        if (cards.isEmpty()) {
            status.visibility = View.VISIBLE; openBtn.visibility = View.VISIBLE
            statusText.text = "No encontré series aquí.\nAbre la web: si pide verificación, resuélvela y vuelve. También puedes ir a una serie y pulsar «Serie»."
        } else status.visibility = View.GONE
    }

    private fun openSeries(c: CoverItem) = startActivity(Intent(this, SeriesActivity::class.java)
        .putExtra(SeriesActivity.EXTRA_URL, c.url).putExtra(SeriesActivity.EXTRA_TITLE, c.title).putExtra(SeriesActivity.EXTRA_COVER, c.image))

    private fun quickAdd(c: CoverItem) {
        val s = Library.obtain(c.url, c.title, c.image)
        if (s.cover.isEmpty()) s.cover = c.image
        Library.setInLibrary(s, !s.inLibrary)
        Toast.makeText(this, if (s.inLibrary) "Añadida a la biblioteca" else "Quitada de la biblioteca", Toast.LENGTH_SHORT).show()
    }

    private fun openWeb(url: String) {
        needsReload = true
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
    }
}
