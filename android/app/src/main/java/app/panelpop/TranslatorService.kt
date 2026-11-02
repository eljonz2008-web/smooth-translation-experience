package app.panelpop

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Servicio en primer plano: burbuja flotante + captura (MediaProjection) + OCR (ML Kit)
 * + traducción local (ML Kit Translate) + capa de superposición.
 *
 * Cómo se evita que las traducciones se queden pegadas o se acumulen:
 *  1. Mientras hay traducciones visibles solo se vigila si la pantalla cambia (firma pequeña,
 *     muy barata). Nunca se hace OCR sobre una imagen que contenga nuestras propias traducciones.
 *  2. En cuanto el contenido se mueve (desplazar, cambiar de página), la capa se vacía al instante.
 *  3. Cuando la pantalla se queda quieta durante [Prefs.settleMs], se lee y se muestra el
 *     resultado nuevo, que SUSTITUYE al anterior. Si la pantalla cambió mientras se leía,
 *     el resultado se descarta.
 *
 * Los fotogramas se procesan en memoria y se descartan; nunca se guardan en disco.
 */
class TranslatorService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_STOP = "stop"
        const val CHANNEL = "panelpop"
        private const val TAG = "PanelPop"
        private const val TICK_MS = 250L
        private const val SIG = 32
        private const val ACCENT = 0xFFFF6A3D.toInt()
        @Volatile var running = false
    }

    private enum class Phase { WAITING, SHOWING }

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private var projection: MediaProjection? = null
    @Volatile private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var overlay: OverlayView? = null
    private var overlayLp: WindowManager.LayoutParams? = null
    private var bubble: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var active = true
    @Volatile private var busy = false
    @Volatile private var screenW = 0
    @Volatile private var screenH = 0
    @Volatile private var scale = 0.65f

    // ----- estado del ciclo (solo se toca desde el hilo de trabajo) -----
    @Volatile private var phase = Phase.WAITING
    @Volatile private var resetRequested = true
    private var clearedAt = 0L
    private var shownAt = 0L
    private var baseline: IntArray? = null
    private var prevSig: IntArray? = null
    private var stableSince = 0L
    private var lastFrameSig: IntArray? = null
    private var lastFrameAt = 0L
    private var lastOcrSig: IntArray? = null
    private var frame: Bitmap? = null
    private var frameValid = false

    private val cache = LruCache<String, String>(500)
    private val translators = HashMap<String, Translator>()
    private val failedAt = HashMap<String, Long>()
    private val langId = LanguageIdentification.getClient()
    private val recognizers = HashMap<String, TextRecognizer>()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> { if (running) setActive(!active) else stopSelf(); return START_NOT_STICKY }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        if (running) return START_NOT_STICKY

        // Debe iniciarse en primer plano ANTES de obtener la proyección (Android 14+)
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notif)

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mpm.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { main.post { stopSelf() } }
            }, main)

            setupCapture()
            addOverlay()
            addBubble()
        } catch (e: Exception) {
            Log.e(TAG, "start", e)
            toast("No se pudo iniciar el traductor")
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        main.post(loop)
        return START_NOT_STICKY
    }

    // ---------- Captura ----------
    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds
        else Rect().also {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            it.set(0, 0, m.widthPixels, m.heightPixels)
        }

    private fun setupCapture() {
        val bounds = screenBounds()
        screenW = bounds.width(); screenH = bounds.height()
        scale = prefs.captureScale
        val w = (screenW * scale).toInt(); val h = (screenH * scale).toInt()
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        vd = projection?.createVirtualDisplay(
            "panelpop", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )
    }

    /** Al girar el teléfono: misma proyección, nuevo tamaño (no se puede crear otra pantalla virtual). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!running) return
        val b = screenBounds()
        if (b.width() == screenW && b.height() == screenH) return
        overlay?.clear()
        worker.execute {
            try {
                screenW = b.width(); screenH = b.height()
                val w = (screenW * scale).toInt(); val h = (screenH * scale).toInt()
                val old = reader
                val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
                vd?.resize(w, h, resources.displayMetrics.densityDpi)
                vd?.surface = r.surface
                reader = r
                old?.close()
                frame?.recycle(); frame = null
                resetCycle()
                main.post {
                    overlayLp?.let { lp ->
                        lp.width = screenW; lp.height = screenH
                        overlay?.let { v -> runCatching { wm.updateViewLayout(v, lp) } }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "rotate", e) }
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            if (active && !busy) {
                busy = true
                try {
                    worker.execute {
                        try { tick() } catch (e: Exception) { Log.e(TAG, "tick", e) } finally { busy = false }
                    }
                } catch (e: Exception) { busy = false }
            }
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun resetCycle() {
        phase = Phase.WAITING
        clearedAt = SystemClock.uptimeMillis()
        baseline = null; prevSig = null; lastOcrSig = null
        lastFrameSig = null; frameValid = false
    }

    /** Se ejecuta en el hilo de trabajo cada [TICK_MS]. */
    private fun tick() {
        if (resetRequested) { resetRequested = false; resetCycle() }
        val now = SystemClock.uptimeMillis()

        // 1) Lee el fotograma más reciente (null = la pantalla no ha cambiado desde el anterior)
        val img = try { reader?.acquireLatestImage() } catch (e: Exception) { null }
        if (img != null) {
            try {
                lastFrameSig = signature(img)
                lastFrameAt = now
                if (phase == Phase.WAITING) { copyFrame(img); frameValid = true } else frameValid = false
            } finally { img.close() }
        }
        val sig = lastFrameSig ?: return

        when (phase) {
            Phase.SHOWING -> {
                // La firma de referencia incluye nuestras traducciones ya dibujadas.
                val base = baseline
                if (base == null) {
                    if (now - shownAt >= 350 && lastFrameAt >= shownAt + 250) baseline = sig
                    return
                }
                if (changed(base, sig)) {
                    // El contenido se movió: fuera traducciones viejas, inmediatamente.
                    main.post { overlay?.clear() }
                    phase = Phase.WAITING
                    clearedAt = now
                    prevSig = null; lastOcrSig = null; frameValid = false
                }
            }
            Phase.WAITING -> {
                if (!frameValid) return
                // Ignora fotogramas que aún podrían contener la capa anterior
                if (lastFrameAt < clearedAt + 150) return
                val prev = prevSig
                if (prev == null || changed(prev, sig)) { prevSig = sig; stableSince = now; return }
                if (now - stableSince < prefs.settleMs) return
                val done = lastOcrSig
                if (done != null && !changed(done, sig)) return // ya leído, no había texto

                val bmp = frame ?: return
                val result = readAndTranslate(bmp)
                lastOcrSig = sig

                // ¿Se movió la pantalla mientras leíamos? Entonces el resultado ya no sirve.
                val check = try { reader?.acquireLatestImage() } catch (e: Exception) { null }
                if (check != null) {
                    val s2 = try { signature(check) } finally { check.close() }
                    lastFrameSig = s2; lastFrameAt = SystemClock.uptimeMillis(); frameValid = false
                    if (changed(sig, s2)) { prevSig = null; lastOcrSig = null; return }
                }
                if (!active || result.isEmpty()) return

                phase = Phase.SHOWING
                baseline = null
                shownAt = SystemClock.uptimeMillis()
                main.post { if (active) overlay?.setBlocks(result) }
            }
        }
    }

    private fun copyFrame(img: Image) {
        val plane = img.planes[0]
        val rowW = plane.rowStride / plane.pixelStride
        var bmp = frame
        if (bmp == null || bmp.width != rowW || bmp.height != img.height) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(rowW, img.height, Bitmap.Config.ARGB_8888)
            frame = bmp
        }
        plane.buffer.rewind()
        bmp!!.copyPixelsFromBuffer(plane.buffer)
    }

    /** Firma 32×32 en escala de grises leída directamente del búfer (sin crear imágenes). */
    private fun signature(img: Image): IntArray {
        val plane = img.planes[0]
        val buf = plane.buffer
        val ps = plane.pixelStride; val rs = plane.rowStride
        val out = IntArray(SIG * SIG)
        val bub = bubbleRectScaled()
        for (gy in 0 until SIG) {
            val y = ((gy + 0.5f) * img.height / SIG).toInt().coerceIn(0, img.height - 1)
            for (gx in 0 until SIG) {
                val x = ((gx + 0.5f) * img.width / SIG).toInt().coerceIn(0, img.width - 1)
                if (bub != null && bub.contains(x, y)) { out[gy * SIG + gx] = -1; continue }
                val i = y * rs + x * ps
                if (i + 2 >= buf.limit()) continue
                val r = buf.get(i).toInt() and 0xFF
                val g = buf.get(i + 1).toInt() and 0xFF
                val b = buf.get(i + 2).toInt() and 0xFF
                out[gy * SIG + gx] = (r * 3 + g * 6 + b) / 10
            }
        }
        return out
    }

    private fun changed(a: IntArray, b: IntArray): Boolean {
        if (a.size != b.size) return true
        var sum = 0L; var cells = 0; var n = 0
        for (i in b.indices) {
            if (a[i] < 0 || b[i] < 0) continue // zona de la burbuja: se ignora
            val d = abs(a[i] - b[i]); sum += d; n++
            if (d > 18) cells++
        }
        if (n == 0) return false
        return sum / n > 3 || cells > n / 60
    }

    /** Burbuja en coordenadas de la captura, con margen, para ignorarla. */
    private fun bubbleRectScaled(): Rect? {
        val lp = bubbleLp ?: return null
        val m = (16 * resources.displayMetrics.density).toInt()
        return Rect(
            ((lp.x - m) * scale).toInt(), ((lp.y - m) * scale).toInt(),
            ((lp.x + lp.width + m) * scale).toInt(), ((lp.y + lp.height + m + statusBar()) * scale).toInt()
        )
    }

    private fun statusBar(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    // ---------- OCR + traducción ----------
    private fun recognizer(lang: String): TextRecognizer = recognizers.getOrPut(lang) {
        when (lang) {
            "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    private fun translator(src: String): Translator? {
        val code = TranslateLanguage.fromLanguageTag(src) ?: return null
        if (code == TranslateLanguage.SPANISH) return null
        translators[code]?.let { return it }
        val failed = failedAt[code]
        if (failed != null && SystemClock.uptimeMillis() - failed < 30_000) return null
        return try {
            val model = TranslateRemoteModel.Builder(code).build()
            val have = Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(model), 10, TimeUnit.SECONDS)
            if (!have) toast("Descargando idioma (solo la primera vez)…")
            val t = Translation.getClient(TranslatorOptions.Builder()
                .setSourceLanguage(code).setTargetLanguage(TranslateLanguage.SPANISH).build())
            Tasks.await(t.downloadModelIfNeeded(), 3, TimeUnit.MINUTES)
            if (!have) toast("Idioma listo")
            translators[code] = t
            failedAt.remove(code)
            t
        } catch (e: Exception) {
            Log.e(TAG, "model $code", e)
            failedAt[code] = SystemClock.uptimeMillis()
            toast("No se pudo descargar el idioma. Revisa tu conexión.")
            null
        }
    }

    private fun isCjk(c: Char): Boolean {
        val b = Character.UnicodeBlock.of(c)
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            b == Character.UnicodeBlock.HIRAGANA || b == Character.UnicodeBlock.KATAKANA ||
            b == Character.UnicodeBlock.HANGUL_SYLLABLES || b == Character.UnicodeBlock.HANGUL_JAMO ||
            b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
    }

    private fun readAndTranslate(bmp: Bitmap): List<TranslatedBlock> {
        val pref = prefs.sourceLang
        val text = Tasks.await(
            recognizer(if (pref == "auto") "ja" else pref).process(InputImage.fromBitmap(bmp, 0)),
            15, TimeUnit.SECONDS
        )
        val inv = 1f / scale
        val bubble = bubbleRectScaled()
        val out = ArrayList<TranslatedBlock>()
        for (block in text.textBlocks) {
            val b0 = block.boundingBox ?: continue
            if (b0.width() <= 0 || b0.height() <= 0) continue
            if (bubble != null && Rect.intersects(bubble, b0)) continue // es nuestra burbuja

            val cjk = block.text.count { isCjk(it) } > block.text.length / 3
            val raw = block.lines.joinToString(if (cjk) "" else " ") { it.text.trim() }.trim()
            if (raw.length < 2 || raw.count { it.isLetter() } < 2) continue

            val src = if (pref == "auto") {
                val l = try { Tasks.await(langId.identifyLanguage(raw), 5, TimeUnit.SECONDS) } catch (e: Exception) { "und" }
                if (l == "und") "en" else l
            } else pref
            // Ya está en español → no hay nada que traducir (y así nunca se traduce a sí mismo)
            if (src == "es") continue

            val key = "$src|$raw"
            val translated = cache.get(key) ?: run {
                val t = translator(src) ?: return@run null
                val r = try { Tasks.await(t.translate(raw), 20, TimeUnit.SECONDS) } catch (e: Exception) { null }
                if (r != null) cache.put(key, r)
                r
            } ?: continue
            if (translated.isBlank()) continue

            val box = Rect((b0.left * inv).toInt(), (b0.top * inv).toInt(),
                (b0.right * inv).toInt(), (b0.bottom * inv).toInt())
            out.add(TranslatedBlock(box, translated.trim(), sampleBackground(bmp, b0)))
        }
        return out
    }

    /** Color más común en el borde del texto original: así el parche se funde con el globo. */
    private fun sampleBackground(bmp: Bitmap, r: Rect): Int {
        val m = 3
        val l = (r.left - m).coerceIn(0, bmp.width - 1); val t = (r.top - m).coerceIn(0, bmp.height - 1)
        val rr = (r.right + m).coerceIn(0, bmp.width - 1); val bb = (r.bottom + m).coerceIn(0, bmp.height - 1)
        val counts = HashMap<Int, Int>()
        fun add(x: Int, y: Int) {
            val c = bmp.getPixel(x, y)
            // agrupa colores parecidos (cuantización a 32 niveles por canal)
            val q = Color.rgb(Color.red(c) and 0xF8, Color.green(c) and 0xF8, Color.blue(c) and 0xF8)
            counts[q] = (counts[q] ?: 0) + 1
        }
        val stepX = ((rr - l) / 24).coerceAtLeast(1); val stepY = ((bb - t) / 12).coerceAtLeast(1)
        var x = l; while (x <= rr) { add(x, t); add(x, bb); x += stepX }
        var y = t; while (y <= bb) { add(l, y); add(rr, y); y += stepY }
        return counts.maxByOrNull { it.value }?.key ?: Color.WHITE
    }

    // ---------- Ventanas flotantes ----------
    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun addOverlay() {
        val v = OverlayView(this, prefs)
        val lp = WindowManager.LayoutParams(
            screenW, screenH, overlayType(),
            // No táctil: los toques pasan a la app de cómics
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        wm.addView(v, lp); overlay = v; overlayLp = lp
    }

    private fun bubbleBg(on: Boolean, d: Float) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (on) intArrayOf(0xFFFF8A4C.toInt(), 0xFFFF4D6D.toInt()) else intArrayOf(0xFF4A4658.toInt(), 0xFF2E2B38.toInt())
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke((2.5f * d).toInt(), Color.argb(230, 255, 255, 255))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val d = resources.displayMetrics.density
        val b = TextView(this).apply {
            text = "ES"; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            background = bubbleBg(true, d)
        }
        val size = (54 * d).toInt()
        val lp = WindowManager.LayoutParams(size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = (10 * d).toInt(); y = (220 * d).toInt()
        }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false; var downAt = 0L
        val longPress = Runnable { b.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); b.alpha = 0.5f }
        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false; downAt = e.eventTime
                    main.postDelayed(longPress, 600)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (!moved && (abs(dx) > 10 * d || abs(dy) > 10 * d)) { moved = true; main.removeCallbacks(longPress) }
                    if (moved) {
                        lp.x = (sx + dx.toInt()).coerceIn(0, (screenW - size).coerceAtLeast(0))
                        lp.y = (sy + dy.toInt()).coerceIn(0, (screenH - size).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(b, lp) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    b.alpha = if (active) 1f else 0.6f
                    if (moved) {
                        // se pega al borde más cercano
                        lp.x = if (lp.x + size / 2 < screenW / 2) (6 * d).toInt() else screenW - size - (6 * d).toInt()
                        runCatching { wm.updateViewLayout(b, lp) }
                    } else if (e.action == MotionEvent.ACTION_UP) {
                        if (e.eventTime - downAt >= 600) stopSelf() // mantener pulsado = cerrar
                        else setActive(!active)                    // tocar = pausar / reanudar
                    }
                }
            }
            true
        }
        wm.addView(b, lp); bubble = b; bubbleLp = lp
    }

    private fun setActive(a: Boolean) {
        active = a
        val d = resources.displayMetrics.density
        bubble?.apply {
            alpha = if (a) 1f else 0.6f
            text = if (a) "ES" else "❚❚"
            background = bubbleBg(a, d)
        }
        overlay?.clear()
        resetRequested = true
        toast(if (a) "Traductor reanudado" else "Traductor en pausa")
        runCatching { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, buildNotification()) }
    }

    private fun toast(msg: String) = main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // ---------- Notificación ----------
    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Traductor PanelPop", NotificationManager.IMPORTANCE_LOW))
    }

    private fun pi(action: String, req: Int) = PendingIntent.getService(this, req,
        Intent(this, TranslatorService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setColor(ACCENT)
            .setContentTitle(if (active) "PanelPop está traduciendo" else "PanelPop en pausa")
            .setContentText(if (active) "Toca la burbuja para pausar · mantenla para cerrar" else "Toca la burbuja para reanudar")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, if (active) "Pausar" else "Reanudar", pi(ACTION_TOGGLE, 1)).build())
            .addAction(Notification.Action.Builder(null, "Cerrar", pi(ACTION_STOP, 2)).build())
            .build()

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        overlay?.let { runCatching { wm.removeView(it) } }; overlay = null
        bubble?.let { runCatching { wm.removeView(it) } }; bubble = null
        runCatching { vd?.release() }; vd = null
        runCatching { projection?.stop() }; projection = null
        worker.execute {
            runCatching { reader?.close() }; reader = null
            frame?.recycle(); frame = null
            translators.values.forEach { runCatching { it.close() } }
            recognizers.values.forEach { runCatching { it.close() } }
            runCatching { langId.close() }
        }
        worker.shutdown()
        super.onDestroy()
    }
}
