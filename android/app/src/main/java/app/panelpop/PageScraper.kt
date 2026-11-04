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
    val desc: String = "",
    val genres: List<String> = emptyList(),
    /** Formulario de búsqueda de la página (para aprender cómo busca la web). */
    val searchAction: String = "",
    val searchField: String = "",
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
    private var last: Scraped? = null
    private var finished = false
    var onPage: ((String) -> Unit)? = null
    var onProgress: ((Scraped) -> Unit)? = null
    /** Modo búsqueda: solo cuentan las series cuyo título coincide con [query]. */
    private var query = ""
    private var typeQuery = false
    private var typed = false

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

    /**
     * Busca [q]: abre [url] (la dirección de búsqueda) o, si [type] es true, la portada y escribe
     * en su caja de búsqueda, como haría una persona.
     */
    fun search(url: String, q: String, type: Boolean) {
        query = q; typeQuery = type
        start(url)
    }

    private fun start(url: String) {
        gen++; finished = false; lastScore = -1; stableTicks = 0; clicks = 0; clicked = false; typed = false; last = null
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
        // Listas de capítulos cortadas ("Ver más", "Mostrar todos"): se pulsan solas
        if (typeQuery && !typed) web.evaluateJavascript(TYPE_JS.replace("__Q__", JSONObject.quote(query))) { r -> if (r == "1") typed = true }
        if (want == Want.CHAPTERS && clicks < MAX_CLICKS) {
            clicked = true
            web.evaluateJavascript(MORE_JS) { r -> clicks += r?.toIntOrNull() ?: 0 }
        }
        web.evaluateJavascript(JS) { res ->
            if (g != gen || finished) return@evaluateJavascript
            val s = parse(res)
            if (s != null) last = s
            val score = s?.let {
                when (want) {
                    Want.CARDS -> if (query.isNotEmpty()) WebParse.searchResults(it.url, it.cards, query).size else WebParse.seriesCards(it.url, it.cards).size
                    Want.CHAPTERS -> WebParse.chapters(it.url, it.links).size
                    Want.IMAGES -> WebParse.pageImages(it.images).size
                }
            } ?: 0
            if (score > lastScore && score > 0 && s != null) onProgress?.invoke(s)
            if (score > 0 && score == lastScore) stableTicks++ else stableTicks = 0
            lastScore = score
            val elapsed = SystemClock.uptimeMillis() - started
            val done = (score > 0 && stableTicks >= 2 && elapsed > 1800) || elapsed > (if (query.isNotEmpty()) SEARCH_TIMEOUT_MS else TIMEOUT_MS)
            if (done) { finished = true; onDone(last) }
            else main.postDelayed({ poll(g) }, 600)
        }
    }

    private fun parse(res: String?): Scraped? = try {
        val str = JSONTokener(res ?: "null").nextValue() as? String
        if (str == null) null else {
            val o = JSONObject(str)
            val cards = ArrayList<WebParse.RawCard>(); val links = ArrayList<WebParse.RawLink>(); val imgs = ArrayList<WebParse.RawImage>()
            o.optJSONArray("cards")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { cards.add(WebParse.RawCard(it.optString("u"), it.optString("t"), it.optString("i"))) } }
            o.optJSONArray("links")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { links.add(WebParse.RawLink(it.optString("u"), it.optString("t"), it.optInt("c") == 1, it.optInt("c") == 2)) } }
            o.optJSONArray("images")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { imgs.add(WebParse.RawImage(it.optString("s"), it.optInt("w"), it.optInt("h"), it.optInt("r") == 1)) } }
            val genres = ArrayList<String>()
            o.optJSONArray("genres")?.let { a -> for (i in 0 until a.length()) genres.add(a.optString(i)) }
            Scraped(o.optString("url"), o.optString("title"), o.optString("cover"), cards, links, imgs, o.optString("next"), o.optString("prev"),
                o.optString("desc"), genres, o.optString("sa"), o.optString("sn"))
        }
    } catch (e: Exception) { null }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val SEARCH_TIMEOUT_MS = 12_000L

        // Escribe la búsqueda en la caja de la web (abre la lupa si hace falta). Devuelve 1 al escribir.
        private const val TYPE_JS = """(function(q){function vis(e){return e&&e.offsetParent!==null}
var ins=[].slice.call(document.querySelectorAll('input[type=search],input[name=s],input[name=q],input[name=search],input[placeholder*="usca" i],input[placeholder*="earch" i]')).filter(vis);
if(!ins.length){var b=[].slice.call(document.querySelectorAll('button,a,[role=button]')).filter(function(e){var t=((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.textContent||'')).toLowerCase();var h=e.getAttribute('href')||'';return vis(e)&&/buscar|search|busca/.test(t)&&!/^https?:/.test(h)&&t.length<40})[0];if(b){try{b.click()}catch(x){}}return 0}
var i=ins[0];i.focus();var st=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;st.call(i,q);
i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new Event('change',{bubbles:true}));
['keydown','keypress','keyup'].forEach(function(n){i.dispatchEvent(new KeyboardEvent(n,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}))});
return 1})(__Q__)"""
        private const val MAX_CLICKS = 40

        // Pulsa botones tipo «Ver más / Mostrar todos» que no lleven a otra página. Devuelve cuántos pulsó.
        private const val MORE_JS = """(function(){var n=0;var els=document.querySelectorAll('button,a,div,span,li');
for(var i=0;i<els.length&&n<3;i++){var e=els[i];if(e.children.length>2)continue;var t=(e.textContent||'').trim();
if(t.length>40||!/^(ver|mostrar|cargar|show|load|view)\s+(m[aá]s|more|todos|todo|all)/i.test(t))continue;
var inner=false;for(var k=0;k<e.children.length;k++){if((e.children[k].textContent||'').trim()==t)inner=true}if(inner)continue;
if(e.tagName=='A'){var h=e.getAttribute('href')||'';if(h&&h!='#'&&h.indexOf('javascript')!=0)continue}
if(e.offsetParent===null)continue;try{e.click();n++}catch(x){}}return n})()"""

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
var r={url:location.href,title:(h1&&h1.textContent.trim())||meta('og:title')||document.title,cover:'',cards:[],links:[],images:[],next:'',prev:''};
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
// Sinopsis, géneros y formulario de búsqueda
var ds=document.querySelector('.summary__content,.description-summary,.manga-excerpt,[class*=synopsis],[class*=sinopsis],[itemprop=description],[class*=summary] p,[class*=description]');
r.desc=((ds&&ds.innerText)||meta('og:description')||meta('description')||'').trim().slice(0,2000);
var GEN=/(genre|genero|g%C3%A9nero|generos|genres|genders|tag|tags|category|categoria)s?([\/=?]|$)/i;r.genres=[];
var ga=document.querySelectorAll('a[href]');for(var gi=0;gi<ga.length&&r.genres.length<15;gi++){var gx=ga[gi];if(!GEN.test(gx.getAttribute('href')||''))continue;
 if(gx.closest('nav,header,footer,aside,[class*=menu],[class*=nav],[class*=footer],[class*=sidebar],[class*=widget]'))continue;var gt=(gx.innerText||'').trim();if(gt&&gt.length<30)r.genres.push(gt)}
var sf=document.querySelector('form input[type=search],form input[name=s],form input[name=q],form input[name=search],form input[name=buscar],form input[name=keyword],form input[name=query]');
if(sf){var fm=sf.closest('form');r.sa=fm.getAttribute('action')||'';r.sn=sf.getAttribute('name')||''}
var READER='.reading-content,#readerarea,.page-break,.container-chapter-reader,.reader-area,.chapter-content,#chapter-images,.chapter-images,.chapter-container,.read-container,[class*=reader],[id*=reader]';
var im=document.querySelectorAll('img');
for(var j=0;j<im.length&&j<800;j++){var g=im[j];var s=src(g);if(!s)continue;
 var w=g.naturalWidth||parseInt(g.getAttribute('width')||'0')||0;var h=g.naturalHeight||parseInt(g.getAttribute('height')||'0')||0;
 r.images.push({s:s,w:w,h:h,r:g.closest(READER)?1:0})}
return JSON.stringify(r)})()"""
    }
}
