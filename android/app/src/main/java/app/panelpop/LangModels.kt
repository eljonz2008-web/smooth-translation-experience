package app.panelpop

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Modelos de traducción descargados al teléfono, compartidos por la pantalla principal y el
 * servicio. Las descargas van SIEMPRE en segundo plano: el traductor nunca se queda bloqueado
 * esperando; mientras falta un idioma simplemente lo salta y vuelve a leer cuando termina.
 */
object LangModels {
    enum class State { READY, DOWNLOADING, FAILED, MISSING }

    private const val TAG = "PanelPop"
    private const val TIMEOUT_MS = 3 * 60_000L
    const val RETRY_MS = 20_000L

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    // El inglés viene incluido en ML Kit
    private val ready = ConcurrentHashMap.newKeySet<String>().apply { add(TranslateLanguage.ENGLISH) }
    private val startedAt = ConcurrentHashMap<String, Long>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val errors = ConcurrentHashMap<String, String>()
    private val translators = ConcurrentHashMap<String, Translator>()
    private val listeners = CopyOnWriteArrayList<(String, State) -> Unit>()
    @Volatile var checked = false; private set

    fun init(ctx: Context) { if (app == null) app = ctx.applicationContext }

    fun addListener(l: (String, State) -> Unit) { listeners.add(l) }
    fun removeListener(l: (String, State) -> Unit) { listeners.remove(l) }
    private fun emit(code: String, s: State) = main.post { listeners.forEach { runCatching { it(code, s) } } }

    fun online(): Boolean {
        val cm = app?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Pregunta a ML Kit qué modelos hay ya en el teléfono. */
    fun refresh(done: (() -> Unit)? = null) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { set ->
                set.forEach { ready.add(it.language) }
                checked = true
                emit("", State.READY)
                done?.invoke()
            }
            .addOnFailureListener { e -> Log.e(TAG, "downloaded models", e); checked = true; done?.invoke() }
    }

    fun state(code: String): State = when {
        ready.contains(code) -> State.READY
        startedAt.containsKey(code) -> State.DOWNLOADING
        failedAt.containsKey(code) -> State.FAILED
        else -> State.MISSING
    }

    fun error(code: String): String? = errors[code]

    /** Estado conjunto de lo necesario para traducir [code] → español. */
    fun pairState(code: String): State {
        val s = listOf(state(code), state(TranslateLanguage.SPANISH))
        return when {
            s.all { it == State.READY } -> State.READY
            s.any { it == State.DOWNLOADING } -> State.DOWNLOADING
            s.any { it == State.FAILED } -> State.FAILED
            else -> State.MISSING
        }
    }

    /** Empieza a descargar [code] si hace falta. Devuelve false si no hay internet. */
    fun download(code: String): Boolean {
        if (ready.contains(code) || startedAt.containsKey(code)) return true
        if (!online()) {
            failedAt[code] = SystemClock.uptimeMillis(); errors[code] = "Sin conexión a internet"
            emit(code, State.FAILED); return false
        }
        val start = SystemClock.uptimeMillis()
        startedAt[code] = start; failedAt.remove(code); errors.remove(code)
        emit(code, State.DOWNLOADING)
        Log.i(TAG, "descargando modelo $code")
        RemoteModelManager.getInstance()
            .download(TranslateRemoteModel.Builder(code).build(), DownloadConditions.Builder().build())
            .addOnSuccessListener {
                startedAt.remove(code); failedAt.remove(code); errors.remove(code)
                ready.add(code)
                Log.i(TAG, "modelo $code listo")
                emit(code, State.READY)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "modelo $code", e)
                startedAt.remove(code)
                failedAt[code] = SystemClock.uptimeMillis()
                errors[code] = if (online()) "No se pudo descargar" else "Sin conexión a internet"
                emit(code, State.FAILED)
            }
        // Si ML Kit no responde, no dejamos al usuario esperando para siempre
        main.postDelayed({
            if (startedAt[code] == start) {
                startedAt.remove(code)
                failedAt[code] = SystemClock.uptimeMillis()
                errors[code] = "La descarga tarda demasiado"
                emit(code, State.FAILED)
            }
        }, TIMEOUT_MS)
        return true
    }

    /** Descarga lo necesario para traducir [code] → español. */
    fun ensurePair(code: String): Boolean {
        val a = download(TranslateLanguage.SPANISH)
        val b = if (code != TranslateLanguage.SPANISH) download(code) else true
        return a && b
    }

    /**
     * Traductor listo para usar, o null si falta algún modelo (en ese caso empieza o reintenta
     * la descarga en segundo plano). Nunca bloquea.
     */
    fun translatorOrDownload(code: String): Translator? {
        if (pairState(code) == State.READY) {
            return translators.getOrPut(code) {
                Translation.getClient(TranslatorOptions.Builder()
                    .setSourceLanguage(code).setTargetLanguage(TranslateLanguage.SPANISH).build())
            }
        }
        val now = SystemClock.uptimeMillis()
        for (c in listOf(TranslateLanguage.SPANISH, code)) {
            val f = failedAt[c]
            if (state(c) == State.MISSING || (f != null && now - f > RETRY_MS)) download(c)
        }
        return null
    }

    fun closeTranslators() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
    }
}
