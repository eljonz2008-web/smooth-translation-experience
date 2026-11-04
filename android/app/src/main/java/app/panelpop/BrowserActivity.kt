package app.panelpop

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar

/**
 * Navegador dentro de la app: para pasar verificaciones de la web o llegar a una serie a mano.
 * «Serie» abre la ficha de la página actual y «Leer» la abre en el lector.
 */
class BrowserActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val ui = Ui(this)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        window.statusBarColor = Pal.BG; window.navigationBarColor = Pal.BG

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Pal.BG) }
        val serie = ui.text("Serie", 13f, Pal.TEXT, true).apply {
            setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9)); background = ui.round(Pal.CARD_HI, 12, Pal.LINE)
            setOnClickListener {
                startActivity(Intent(this@BrowserActivity, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_URL, web.url ?: url)
                    .putExtra(SeriesActivity.EXTRA_TITLE, web.title ?: ""))
            }
        }
        val leer = ui.text("Leer", 13f, android.graphics.Color.WHITE, true).apply {
            setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9)); background = ui.gradient(12)
            setOnClickListener {
                startActivity(Intent(this@BrowserActivity, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_CHAPTER, web.url ?: url))
            }
        }
        val (bar, title) = ui.topBar(WebParse.host(url), { finish() }, serie, leer)
        title.textSize = 15f
        col.addView(bar)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Pal.ACCENT)
        }
        col.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(3)))

        web = WebView(this)
        Web.setup(web)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = !Web.allowed(req.url.toString())
            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { title.text = WebParse.host(u) }
            override fun onPageFinished(view: WebView, u: String) { title.text = view.title?.let { WebParse.cleanTitle(it) }?.ifEmpty { null } ?: WebParse.host(u) }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { progress.progress = p; progress.alpha = if (p >= 100) 0f else 1f }
        }
        col.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)
        web.loadUrl(url)
    }

    @Deprecated("Activity API")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }

    companion object { const val EXTRA_URL = "url" }
}
