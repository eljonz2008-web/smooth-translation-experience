package app.panelpop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Lector propio: saca las páginas del capítulo de la web y las muestra limpias, una debajo
 * de otra, sin anuncios. Botón ES para encender el traductor encima.
 */
class ReaderActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var web: WebView
    private lateinit var scraper: PageScraper
    private lateinit var loading: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var esBtn: TextView
    private lateinit var prevBtn: TextView
    private lateinit var nextBtn: TextView
    private lateinit var failBox: LinearLayout
    private val main = Handler(Looper.getMainLooper())
    private var series: Library.Series? = null
    private var chapterUrl = ""
    private var prevUrl: String? = null
    private var nextUrl: String? = null
    private var barsVisible = true

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Library.init(this)
        ui = Ui(this)
        chapterUrl = intent.getStringExtra(EXTRA_CHAPTER) ?: run { finish(); return }
        series = intent.getStringExtra(EXTRA_SERIES)?.let { Library.get(it) ?: Library.obtain(it) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply { setBackgroundColor(Pal.BG) }
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Barra superior
        esBtn = ui.text("ES", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(ui.dp(44), ui.dp(44))
            setOnClickListener { toggleTranslator() }
        }
        val (bar, tv) = ui.topBar("", { finish() }, esBtn)
        topBar = bar; titleView = tv; titleView.textSize = 16f
        topBar.setBackgroundColor(Color.argb(235, 15, 13, 20))
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Barra inferior: anterior / siguiente
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(14))
            setBackgroundColor(Color.argb(235, 15, 13, 20))
        }
        prevBtn = ui.button("◀  Anterior", false) { prevUrl?.let { open(it) } }
        nextBtn = ui.button("Siguiente  ▶") { nextUrl?.let { open(it) } }
        bottomBar.addView(prevBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottomBar.addView(nextBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(10) })
        root.addView(bottomBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        // Cargando
        loading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Pal.BG)
            isClickable = true
        }
        loading.addView(ProgressBar(this).apply { isIndeterminate = true; indeterminateTintList = android.content.res.ColorStateList.valueOf(Pal.ACCENT) })
        loadingText = ui.text("Cargando capítulo…", 15f, Pal.MUTED).apply { gravity = Gravity.CENTER }
        loading.addView(loadingText, ui.lp(top = 14))
        root.addView(loading, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { topMargin = ui.dp(64) })

        // Aviso si no se encontraron páginas
        failBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
            background = ui.round(Pal.CARD, 18, Pal.LINE); visibility = View.GONE
        }
        failBox.addView(ui.text("No encontré las páginas. Si la web pide verificación, resuélvela aquí y toca «Reintentar».", 14f))
        val fb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fb.addView(ui.button("Leer en la web", false) { failBox.visibility = View.GONE },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fb.addView(ui.button("Reintentar") { failBox.visibility = View.GONE; showLoading("Buscando páginas…"); scraper.rescan() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(10) })
        failBox.addView(fb, ui.lp(top = 12))
        root.addView(failBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            .apply { setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(90)) })

        setContentView(root)
        scraper = PageScraper(web, PageScraper.Want.IMAGES) { res -> onScraped(res) }
        addJs()
        open(chapterUrl)
    }

    override fun onResume() { super.onResume(); paintEs() }

    override fun onDestroy() { scraper.cancel(); web.destroy(); super.onDestroy() }

    private fun showLoading(msg: String) { loadingText.text = msg; loading.visibility = View.VISIBLE }

    private fun open(url: String) {
        chapterUrl = url
        failBox.visibility = View.GONE
        val chs = series?.chapters ?: emptyList()
        val (p, n) = WebParse.neighbors(chs, url)
        prevUrl = p?.url; nextUrl = n?.url
        val name = chs.firstOrNull { WebParse.key(it.url) == WebParse.key(url) }?.name
        titleView.text = listOfNotNull(name, series?.title).joinToString(" · ").ifEmpty { WebParse.host(url) }
        paintNav()
        showLoading("Cargando capítulo…")
        scraper.load(url)
    }

    private fun onScraped(res: Scraped?) {
        val pages = res?.let { WebParse.pageImages(it.images) } ?: emptyList()
        // Sin lista de capítulos (abierto desde el navegador): usa los enlaces de la propia página
        if (res != null) {
            if (prevUrl == null && res.prev.isNotEmpty() && WebParse.sameSite(res.prev, chapterUrl) && WebParse.key(res.prev) != WebParse.key(chapterUrl)) prevUrl = res.prev
            if (nextUrl == null && res.next.isNotEmpty() && WebParse.sameSite(res.next, chapterUrl) && WebParse.key(res.next) != WebParse.key(chapterUrl)) nextUrl = res.next
            if (series == null && titleView.text.toString() == WebParse.host(chapterUrl)) titleView.text = WebParse.cleanTitle(res.title).ifEmpty { titleView.text.toString() }
        }
        paintNav()
        if (pages.isEmpty()) {
            loading.visibility = View.GONE; failBox.visibility = View.VISIBLE; return
        }
        web.loadDataWithBaseURL(chapterUrl, html(pages), "text/html", "utf-8", chapterUrl)
        loading.postDelayed({ loading.visibility = View.GONE; setBars(false) }, 350)
    }

    private fun paintNav() {
        prevBtn.alpha = if (prevUrl == null) 0.4f else 1f
        nextBtn.alpha = if (nextUrl == null) 0.4f else 1f
    }

    private fun setBars(show: Boolean) {
        barsVisible = show
        val v = if (show) View.VISIBLE else View.GONE
        topBar.visibility = v; bottomBar.visibility = v
    }

    private fun paintEs() {
        val on = TranslatorService.running
        esBtn.background = if (on) ui.gradient(22) else ui.round(Pal.CARD_HI, 22, Pal.LINE)
    }

    private fun toggleTranslator() {
        if (TranslatorService.running) {
            Capture.stop(this); Toast.makeText(this, "Traductor apagado", Toast.LENGTH_SHORT).show()
            main.postDelayed({ paintEs() }, 400)
        } else Capture.request(this)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
        if (Capture.onResult(this, req, res, data)) {
            Toast.makeText(this, "Traductor activado", Toast.LENGTH_SHORT).show()
            setBars(false)
            main.postDelayed({ paintEs() }, 500)
        }
    }

    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    private fun addJs() {
        web.addJavascriptInterface(object {
            @JavascriptInterface fun tap() { main.post { setBars(!barsVisible) } }
            @JavascriptInterface fun read() { main.post { series?.let { Library.markRead(it, chapterUrl) } } }
            @JavascriptInterface fun go(dir: String) { main.post { (if (dir == "next") nextUrl else prevUrl)?.let { open(it) } } }
        }, "PP")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private fun html(pages: List<String>): String {
        val sb = StringBuilder()
        sb.append("""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5">
<meta name="referrer" content="unsafe-url"><style>
html,body{margin:0;background:#0f0d14;-webkit-tap-highlight-color:transparent}
img{display:block;width:100%;height:auto;min-height:60vh;background:#1b1822;border:0}
img.ok{min-height:0;background:none}
.end{padding:48px 20px 140px;text-align:center;color:#a09ab0;font:15px sans-serif}
.end b{display:block;color:#f6f2ec;font-size:20px;margin-bottom:18px}
.btn{display:inline-block;margin:6px;padding:14px 22px;border-radius:14px;background:linear-gradient(135deg,#ff6a3d,#ff4d6d);color:#fff;font-weight:700;text-decoration:none}
.alt{background:#262230;color:#f6f2ec}.off{opacity:.35;pointer-events:none}
</style></head><body>""")
        pages.forEachIndexed { i, p ->
            sb.append("<img src=\"").append(esc(p)).append("\"")
            if (i > 2) sb.append(" loading=\"lazy\"")
            sb.append(" onload=\"this.className='ok'\">")
        }
        sb.append("<div class=\"end\"><b>Fin del capítulo</b>")
        sb.append("<a class=\"btn alt").append(if (prevUrl == null) " off" else "").append("\" href=\"#\" onclick=\"PP.go('prev');return false\">◀ Anterior</a>")
        sb.append("<a class=\"btn").append(if (nextUrl == null) " off" else "").append("\" href=\"#\" onclick=\"PP.go('next');return false\">Siguiente ▶</a></div>")
        sb.append("""<script>
var sent=false;
document.addEventListener('click',function(e){if(e.target.closest&&e.target.closest('a'))return;PP.tap()});
window.addEventListener('scroll',function(){var h=document.documentElement;if(!sent&&h.scrollTop+innerHeight>=h.scrollHeight-innerHeight*1.5){sent=true;PP.read()}},{passive:true});
</script></body></html>""")
        return sb.toString()
    }

    companion object {
        const val EXTRA_SERIES = "series"
        const val EXTRA_CHAPTER = "chapter"
    }
}
