package app.panelpop

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener

/** Lo que se ha leído de una página web (en bruto; WebParse decide qué es cada cosa). */
class Scraped(
    val url: String,
    val title: String,
    val cover: String,
    val cards: List<WebParse.RawCard>,
    val links: List<WebParse.RawLink>,
    val images: List<WebParse.RawImage>,
    val next: String,
    val prev: String,
    /** Enlaces cortos sin número (menús): para encontrar el catálogo completo. */
    val nav: List<WebParse.RawLink> = emptyList(),
)

object Web {
    @SuppressLint("SetJavaScriptEnabled")
    fun setup(w: WebView) {
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false) // sin ventanas emergentes de anuncios
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
    }

    /** Solo se navega por http(s); los intent:// y market:// de los anuncios se bloquean. */
    fun allowed(url: String) = url.startsWith("http://") || url.startsWith("https://")
}

/**
 * Abre una página en un WebView (oculto o no), espera a que cargue lo que va saliendo por JS
 * (capítulos por AJAX, imágenes perezosas, botones «Ver más»…) y devuelve lo encontrado cuando
 * deja de cambiar. [onProgress] avisa en cuanto aparece algo, para no hacer esperar al usuario.
 */
class PageScraper(private val web: WebView, private val want: Want, private val onDone: (Scraped?) -> Unit) {
    enum class Want { CARDS, CHAPTERS, IMAGES }

    private val main = Handler(Looper.getMainLooper())
    private var gen = 0
    private var started = 0L
    private var lastScore = -1
    private var stableTicks = 0
    private var clicks = 0
    private var lastClicks = 0
    private var last: Scraped? = null
    private var finished = false
    var onPage: ((String) -> Unit)? = null
    var onProgress: ((Scraped) -> Unit)? = null
    /** Modo búsqueda: solo cuentan las series cuyo título coincide con [query]. */
    private var query = ""
    private var typeQuery = false
    private var typed = false
    // Lo visto en lecturas anteriores de la misma página (listas que borran lo de arriba al bajar)
    private val seenCards = LinkedHashMap<String, WebParse.RawCard>()
    private val seenLinks = LinkedHashMap<String, WebParse.RawLink>()

    init {
        Web.setup(web)
        // Para series y capítulos no hacen falta las fotos: la página carga mucho más rápido
        if (want != Want.IMAGES) web.settings.blockNetworkImage = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url.toString()
                if (!Web.allowed(u)) return true
                // Después de pulsar «Ver más», algunas webs abren anuncios de otra web: se bloquean
                return clicked && req.isForMainFrame && !req.isRedirect && here.isNotEmpty() && !WebParse.sameSite(u, here)
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { here = url; onPage?.invoke(url) }
        }
    }

    private var here = ""
    private var clicked = false

    fun load(url: String) {
        query = ""; typeQuery = false
        start(url)
    }

    /** Busca [q]: abre [url] o, si es null, la portada [home] y escribe en su caja de búsqueda. */
    fun search(url: String?, home: String, q: String) {
        query = q; typeQuery = url == null
        start(url ?: home)
    }

    private fun start(url: String) {
        gen++; finished = false; lastScore = -1; stableTicks = 0; clicks = 0; clicked = false; typed = false; last = null
        seenCards.clear(); seenLinks.clear()
        started = SystemClock.uptimeMillis()
        here = url
        web.loadUrl(url)
        val g = gen
        main.postDelayed({ poll(g) }, 700)
    }

    /** Vuelve a leer la página actual sin recargarla (p. ej. tras pasar la verificación). */
    fun rescan() {
        gen++; finished = false; lastScore = -1; stableTicks = 0; clicks = 0
        started = SystemClock.uptimeMillis()
        val g = gen
        main.postDelayed({ poll(g) }, 300)
    }

    fun cancel() { gen++; finished = true }

    private fun poll(g: Int) {
        if (g != gen || finished) return
        if (typeQuery && !typed) web.evaluateJavascript(TYPE_JS.replace("__Q__", JSONObject.quote(query))) { r -> if (r == "1") { typed = true; seenCards.clear() } }
        // Bajar hasta el final: muchas webs cargan más series/capítulos al hacer scroll
        if (want != Want.IMAGES) web.evaluateJavascript(SCROLL_JS, null)
        // Listas cortadas ("Ver más", "Cargar 24 capítulos/series más"): se pulsan solas
        if (want != Want.IMAGES && clicks < MAX_CLICKS) {
            clicked = true
            web.evaluateJavascript(moreJs()) { r -> clicks += r?.toIntOrNull() ?: 0 }
        }
        web.evaluateJavascript(JS) { res ->
            if (g != gen || finished) return@evaluateJavascript
            val s = parse(res)?.let { merge(it) }
            if (s != null) last = s
            val score = s?.let {
                when (want) {
                    Want.CARDS -> if (query.isNotEmpty()) WebParse.searchResults(it.url, it.cards, query).size else WebParse.seriesCards(it.url, it.cards).size
                    Want.CHAPTERS -> WebParse.chapters(it.url, it.links).size
                    Want.IMAGES -> WebParse.pageImages(it.images).size
                }
            } ?: 0
            if (score > lastScore && score > 0 && s != null) onProgress?.invoke(s)
            // No basta con que el número se repita una vez: al pulsar «Ver más» o hacer scroll,
            // la web puede tardar varios segundos en dibujar el siguiente grupo.
            if (score > 0 && score == lastScore && clicks == lastClicks) stableTicks++ else stableTicks = 0
            lastScore = score; lastClicks = clicks
            val elapsed = SystemClock.uptimeMillis() - started
            val minElapsed = if (want == Want.IMAGES) 1800 else 3500
            val done = (score > 0 && stableTicks >= 3 && elapsed > minElapsed) || elapsed > (if (query.isNotEmpty()) SEARCH_TIMEOUT_MS else TIMEOUT_MS)
            if (done) { finished = true; onDone(last) }
            else main.postDelayed({ poll(g) }, 600)
        }
    }

    private fun merge(s: Scraped): Scraped {
        // Si la web cambió de página (redirección), lo anterior ya no vale
        if (seenCards.isNotEmpty() || seenLinks.isNotEmpty()) {
            val prevUrl = last?.url ?: ""
            if (prevUrl.isNotEmpty() && WebParse.key(prevUrl) != WebParse.key(s.url) && !typeQuery) { seenCards.clear(); seenLinks.clear() }
        }
        for (c in s.cards) { val k = WebParse.key(c.url); val p = seenCards[k]; if (p == null || (p.title.isEmpty() && c.title.isNotEmpty())) seenCards[k] = c }
        for (l in s.links) { val k = WebParse.key(l.url); val p = seenLinks[k]; if (p == null || (p.fromData && !l.fromData) || (!p.inList && l.inList)) seenLinks[k] = l }
        return Scraped(s.url, s.title, s.cover, seenCards.values.toList(), seenLinks.values.toList(), s.images, s.next, s.prev, s.nav)
    }

    private fun parse(res: String?): Scraped? = try {
        val str = JSONTokener(res ?: "null").nextValue() as? String
        if (str == null) null else {
            val o = JSONObject(str)
            val cards = ArrayList<WebParse.RawCard>(); val links = ArrayList<WebParse.RawLink>(); val imgs = ArrayList<WebParse.RawImage>()
            o.optJSONArray("cards")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { cards.add(WebParse.RawCard(it.optString("u"), it.optString("t"), it.optString("i"))) } }
            o.optJSONArray("links")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { links.add(WebParse.RawLink(it.optString("u"), it.optString("t"), it.optInt("c") == 1, it.optInt("c") == 2)) } }
            o.optJSONArray("images")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { imgs.add(WebParse.RawImage(it.optString("s"), it.optInt("w"), it.optInt("h"), it.optInt("r") == 1)) } }
            val nav = ArrayList<WebParse.RawLink>()
            o.optJSONArray("nav")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { nav.add(WebParse.RawLink(it.optString("u"), it.optString("t"))) } }
            Scraped(o.optString("url"), o.optString("title"), o.optString("cover"), cards, links, imgs, o.optString("next"), o.optString("prev"), nav)
        }
    } catch (e: Exception) { null }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val SEARCH_TIMEOUT_MS = 12_000L

        private const val SCROLL_JS = """(function(){try{window.scrollTo(0,document.documentElement.scrollHeight);
var b=document.querySelectorAll('[class*=chapter],[id*=chapter],[class*=list],[class*=scroll]');for(var i=0;i<b.length&&i<60;i++){var e=b[i];if(e.scrollHeight>e.clientHeight+20)e.scrollTop=e.scrollHeight}}catch(x){}})()"""

        // Escribe la búsqueda en la caja de la web (abre la lupa si hace falta). Devuelve 1 al escribir.
        private const val TYPE_JS = """(function(q){function vis(e){return e&&e.offsetParent!==null}
var ins=[].slice.call(document.querySelectorAll('input[type=search],input[name=s],input[name=q],input[name=search],input[placeholder*="usca" i],input[placeholder*="earch" i]')).filter(vis);
if(!ins.length){var b=[].slice.call(document.querySelectorAll('button,a,[role=button]')).filter(function(e){var t=((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.textContent||'')).toLowerCase();var h=e.getAttribute('href')||'';return vis(e)&&/buscar|search|busca/.test(t)&&!/^https?:/.test(h)&&t.length<40})[0];if(b){try{b.click()}catch(x){}}return 0}
var i=ins[0];i.focus();var st=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;st.call(i,q);
i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new Event('change',{bubbles:true}));
['keydown','keypress','keyup'].forEach(function(n){i.dispatchEvent(new KeyboardEvent(n,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}))});
return 1})(__Q__)"""
        private const val MAX_CLICKS = 40

        // Pulsa botones tipo «Ver más / Cargar 24 capítulos más» que no lleven a otra página.
        private fun moreJs() = MORE_JS.replace("__LOAD_MORE__", JSONObject.quote(WebParse.LOAD_MORE_PATTERN))
        private const val MORE_JS = """(function(){var els=document.querySelectorAll('button,a,div,span,li'),best=null,bs=99;
for(var i=0;i<els.length;i++){var e=els[i];if(e.children.length>2)continue;var t=(e.textContent||'').trim().replace(/\s+/g,' ').replace(/[^A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ\s].*$/,'').trim();
if(t.length>60||!new RegExp(__LOAD_MORE__,'i').test(t))continue;
var inner=false;for(var k=0;k<e.children.length;k++){if((e.children[k].textContent||'').trim()==t)inner=true}if(inner)continue;
if(e.tagName=='A'){var h=e.getAttribute('href')||'';if(h&&h!='#'&&h.indexOf('javascript')!=0)continue}
if(e.offsetParent===null)continue;
// Los botones «Cargar 65 capítulos más» van antes que «Ver más» de la sinopsis.
var score=/\d/.test(t)?0:(/cap[ií]tulo|chapter|series|manhwa|manga|episodio|episode|comic/i.test(t)?1:2);
if(score<bs){best=e;bs=score}}
if(best){try{best.click();return 1}catch(x){}}return 0})()"""

        // Sirve para casi todas las webs de mangas (WordPress Madara, MangaThemesia y genéricas)
        private const val JS = """(function(){
function abs(u){try{return new URL(u,location.href).href}catch(e){return ''}}
function src(img){
 var c=[img.getAttribute('data-src'),img.getAttribute('data-lazy-src'),img.getAttribute('data-original'),img.getAttribute('data-url'),img.getAttribute('data-cfsrc'),img.getAttribute('data-wpfc-original-src')];
 var ss=img.getAttribute('data-srcset')||img.getAttribute('srcset');
 if(ss){c.push(ss.split(',').pop().trim().split(' ')[0])}
 c.push(img.currentSrc);c.push(img.getAttribute('src'));
 for(var i=0;i<c.length;i++){var v=(c[i]||'').trim();if(v&&v.indexOf('data:')!==0)return abs(v)}
 return ''}
function bgOf(el){var m=/url\(["']?([^"')]+)/.exec(el.getAttribute('style')||'');return m?abs(m[1]):''}
function meta(n){var m=document.querySelector('meta[property="'+n+'"],meta[name="'+n+'"]');return m?(m.getAttribute('content')||''):''}
var h1=document.querySelector('h1');
var r={url:location.href,title:(h1&&h1.textContent.trim())||meta('og:title')||document.title,cover:'',cards:[],links:[],images:[],next:'',prev:'',nav:[]};
var ci=document.querySelector('.summary_image img,.thumb img,.manga-thumb img,.series-thumb img,.manga-info-pic img,.info-image img,.cover img,img.cover,.poster img');
r.cover=(ci&&src(ci))||abs(meta('og:image')||'');
var LIST='.wp-manga-chapter,#chapterlist,.chapter-list,.chapters,.eplister,.listing-chapters_wrap,.version-chap,.chapter-item,.row-content-chapter,.list-chapter,[class*=chapter],[id*=chapter],[class*=episode]';
var TT='h3,h4,h5,.post-title,.tt,.title,[class*=title]';
var as=document.querySelectorAll('a[href]');
for(var i=0;i<as.length&&i<5000;i++){var a=as[i];var href=abs(a.getAttribute('href'));if(!href||href.indexOf('http')!==0)continue;
 var txt=(a.innerText||a.getAttribute('title')||'').trim().replace(/\s+/g,' ').slice(0,120);
 var img=a.querySelector('img');var bg='';
 if(!img){var be=a.matches('[style*=background-image]')?a:a.querySelector('[style*=background-image]');if(be)bg=bgOf(be)}
 if(img||bg){var box=a.closest('.page-item-detail,.manga-item,.bs,.bsx,.utao,.item,article,li,.card,[class*=card]');
  var tt=a.querySelector(TT);if(!tt&&box&&box.querySelectorAll('a img,a [style*=background-image]').length<=2)tt=box.querySelector(TT);
  r.cards.push({u:href,t:((tt&&tt.textContent)||a.getAttribute('title')||a.getAttribute('aria-label')||(img&&img.getAttribute('alt'))||txt||'').trim().replace(/\s+/g,' ').slice(0,120),i:img?src(img):bg})}
 if(txt&&txt.length<30&&!/\d/.test(txt)&&r.nav.length<300)r.nav.push({u:href,t:txt});
 if(/\d/.test(txt+href)){r.links.push({u:href,t:txt,c:a.closest(LIST)?1:0})}
 var hint=((a.getAttribute('rel')||'')+' '+(a.className||'')+' '+txt).toLowerCase();
 if(!r.next&&/(^|\s)(next|siguiente|sig\.?|pr[oó]ximo)(\s|$|_)|next_page|nextchap|next-chap/.test(hint))r.next=href;
 if(!r.prev&&/(^|\s)(prev|previous|anterior|ant\.?)(\s|$|_)|prev_page|prevchap|prev-chap/.test(hint))r.prev=href;
}
// Direcciones escondidas en los datos de la página (listas que se dibujan al bajar, webs tipo app)
var seenL={};for(var z=0;z<r.links.length;z++)seenL[r.links[z].u]=1;
var sc=document.querySelectorAll('script:not([src])');var budget=2000000,nd=0;
for(var q=0;q<sc.length&&nd<3000&&budget>0;q++){var tx=sc[q].textContent||'';if(tx.length<40)continue;budget-=tx.length;
 var ms=tx.replace(/\\\//g,'/').match(/https?:\/\/[^"'\s<>\\)]+/g)||[];
 for(var y=0;y<ms.length&&nd<3000;y++){var u=ms[y];if(seenL[u]||!/\d/.test(u))continue;var hh='';try{hh=new URL(u).host}catch(e){continue}
  if(hh.replace(/^www\./,'')!==location.host.replace(/^www\./,''))continue;seenL[u]=1;nd++;r.links.push({u:u,t:'',c:2})}}
var READER='.reading-content,#readerarea,.page-break,.container-chapter-reader,.reader-area,.chapter-content,#chapter-images,.chapter-images,.chapter-container,.read-container,[class*=reader],[id*=reader]';
var im=document.querySelectorAll('img');
for(var j=0;j<im.length&&j<800;j++){var g=im[j];var s=src(g);if(!s)continue;
 var w=g.naturalWidth||parseInt(g.getAttribute('width')||'0')||0;var h=g.naturalHeight||parseInt(g.getAttribute('height')||'0')||0;
 r.images.push({s:s,w:w,h:h,r:g.closest(READER)?1:0})}
return JSON.stringify(r)})()"""
    }
}
