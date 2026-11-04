package app.panelpop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Carga portadas con las mismas cookies y navegador que el WebView, para que las webs con
 * protección (Cloudflare, anti-hotlink) también muestren las imágenes. Caché en memoria y disco.
 */
object ImageLoader {
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private var dir: File? = null
    private var ua: String? = null

    fun userAgent(ctx: Context): String = ua ?: try { WebSettings.getDefaultUserAgent(ctx) } catch (e: Exception) { "Mozilla/5.0 (Linux; Android) Mobile" }.also { ua = it }

    private fun hash(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun load(view: ImageView, url: String, referer: String) {
        view.tag = url
        if (url.isEmpty()) { view.setImageDrawable(null); return }
        mem.get(url)?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        val ctx = view.context.applicationContext
        if (dir == null) dir = File(ctx.cacheDir, "covers").apply { mkdirs() }
        val agent = userAgent(ctx)
        pool.execute {
            val bmp = try { fetch(url, referer, agent) } catch (e: Exception) { null }
            if (bmp != null) mem.put(url, bmp)
            main.post { if (view.tag == url && bmp != null) view.setImageBitmap(bmp) }
        }
    }

    private fun fetch(url: String, referer: String, agent: String): Bitmap? {
        val f = File(dir, hash(url))
        val bytes = if (f.exists() && f.length() > 0) f.readBytes() else {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 20_000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", agent)
            c.setRequestProperty("Referer", referer)
            c.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
            CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
            try {
                if (c.responseCode !in 200..299) return null
                c.inputStream.use { it.readBytes() }.also { f.writeBytes(it) }
            } finally { c.disconnect() }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 300) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
