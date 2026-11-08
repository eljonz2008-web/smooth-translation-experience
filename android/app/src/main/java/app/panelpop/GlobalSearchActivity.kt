package app.panelpop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** Buscador general: busca la misma serie en todos los repositorios a la vez (como Mihon). */
class GlobalSearchActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var results: LinearLayout
    private lateinit var webs: FrameLayout
    private val jobs = ArrayList<Pair<PageScraper, WebView>>()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Library.init(this)
        ui = Ui(this)
        window.statusBarColor = Pal.BG; window.navigationBarColor = Pal.BG
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Pal.BG) }
        col.addView(ui.topBar("Buscar en todas", { finish() }).first)

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(10)) }
        val input = EditText(this).apply {
            hint = "Nombre del manhwa"; setHintTextColor(Pal.MUTED); setTextColor(Pal.TEXT); textSize = 15f
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = ui.round(Pal.CARD, 14, Pal.LINE); setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
            setText(intent.getStringExtra(EXTRA_Q) ?: "")
        }
        fun go() {
            val q = input.text.toString().trim()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
            if (q.length >= 2) run(q)
        }
        input.setOnEditorActionListener { _, a, e -> if (a == EditorInfo.IME_ACTION_SEARCH || e?.keyCode == KeyEvent.KEYCODE_ENTER) { go(); true } else false }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(ui.button("Buscar") { go() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(8) })
        col.addView(bar)

        // Detrás quedan las webs abiertas (invisibles), delante los resultados
        val body = FrameLayout(this)
        webs = FrameLayout(this)
        body.addView(webs, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, ui.dp(24)) }
        body.addView(ScrollView(this).apply { setBackgroundColor(Pal.BG); isVerticalScrollBarEnabled = false; addView(results) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)

        if (Library.repos.isEmpty()) results.addView(ui.empty("No tienes repositorios", "Añade tus webs en Explorar para buscar en todas a la vez."))
        else if (input.text.isNotEmpty()) run(input.text.toString().trim())
        else input.requestFocus()
    }

    override fun onDestroy() { stop(); super.onDestroy() }

    private fun stop() {
        for ((sc, w) in jobs) { sc.cancel(); w.destroy() }
        jobs.clear(); webs.removeAllViews()
    }

    private fun run(q: String) {
        stop(); results.removeAllViews()
        if (Library.repos.isEmpty()) return
        for (r in Library.repos.toList()) {
            val sec = Section(r)
            results.addView(sec.view)
            val w = WebView(this)
            webs.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val sc = PageScraper(w, PageScraper.Want.CARDS) { res -> sec.show(res, q, true) }
            sc.onProgress = { res -> sec.show(res, q, false) }
            jobs.add(sc to w)
            sc.search(WebParse.searchUrl(r.url, q), r.url, q)
        }
    }

    /** Fila de resultados de un repositorio. */
    private inner class Section(private val repo: Library.Repo) {
        val view = LinearLayout(this@GlobalSearchActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, ui.dp(10), 0, ui.dp(4)) }
        private val status = ui.text("Buscando…", 13f, Pal.MUTED)
        private val spin = ProgressBar(this@GlobalSearchActivity).apply { isIndeterminate = true; indeterminateTintList = android.content.res.ColorStateList.valueOf(Pal.ACCENT) }
        private val row = LinearLayout(this@GlobalSearchActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ui.dp(12), 0, ui.dp(12), 0) }
        private var count = 0

        init {
            val head = LinearLayout(this@GlobalSearchActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(ui.dp(16), 0, ui.dp(12), ui.dp(8)) }
            val names = LinearLayout(this@GlobalSearchActivity).apply { orientation = LinearLayout.VERTICAL }
            names.addView(ui.text(repo.name, 16f, Pal.TEXT, true))
            names.addView(status)
            head.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(spin, LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
            view.addView(head)
            view.addView(HorizontalScrollView(this@GlobalSearchActivity).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }

        fun show(res: Scraped?, q: String, done: Boolean) {
            val here = res?.url?.ifEmpty { null } ?: repo.url
            val cards = res?.let { WebParse.searchResults(here, it.cards, q) } ?: emptyList()
            if (cards.size > count) {
                row.removeAllViews(); count = cards.size
                for (c in cards.take(30)) row.addView(cell(c, here))
            }
            status.text = when {
                count > 0 -> "$count resultado${if (count == 1) "" else "s"}"
                done && res == null -> "No se pudo leer esta web"
                done -> "Sin resultados"
                else -> "Buscando…"
            }
            if (done) spin.visibility = View.GONE
        }

        private fun cell(c: WebParse.Card, referer: String): View {
            val w = ui.dp(110)
            val box = LinearLayout(this@GlobalSearchActivity).apply {
                orientation = LinearLayout.VERTICAL; isClickable = true
                setOnClickListener {
                    startActivity(Intent(this@GlobalSearchActivity, SeriesActivity::class.java)
                        .putExtra(SeriesActivity.EXTRA_URL, c.url).putExtra(SeriesActivity.EXTRA_TITLE, c.title).putExtra(SeriesActivity.EXTRA_COVER, c.image))
                }
            }
            val img = ImageView(this@GlobalSearchActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = ui.round(Pal.CARD, 10); clipToOutline = true }
            ImageLoader.load(img, c.image, referer)
            box.addView(img, LinearLayout.LayoutParams(w, (w * 1.5f).toInt()))
            box.addView(ui.text(c.title, 12.5f, Pal.TEXT).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(6) })
            box.layoutParams = LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(10) }
            return box
        }
    }

    companion object { const val EXTRA_Q = "q" }
}
