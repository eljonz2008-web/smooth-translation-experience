package app.panelpop

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Pantalla principal: estado, botón ACTIVAR TRADUCTOR y ajustes. Hecha en código (sin XML). */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var mainBtn: LinearLayout
    private lateinit var mainTitle: TextView
    private lateinit var mainSub: TextView
    private lateinit var permOverlay: PermRow
    private lateinit var permNotif: PermRow
    private lateinit var statusPill: TextView
    private val langRows = ArrayList<LangRow>()
    private val modelListener: (String, LangModels.State) -> Unit = { _, _ -> langRows.forEach { it.update() } }
    private val REQ_CAPTURE = 42
    private val REQ_NOTIF = 7

    // Paleta
    private val BG = Color.rgb(15, 13, 20)
    private val CARD = Color.rgb(27, 24, 34)
    private val CARD_HI = Color.rgb(38, 34, 48)
    private val LINE = Color.rgb(48, 44, 60)
    private val TEXT = Color.rgb(246, 242, 236)
    private val MUTED = Color.rgb(160, 154, 176)
    private val ACCENT = Color.rgb(255, 106, 61)
    private val ACCENT2 = Color.rgb(255, 77, 109)
    private val OK = Color.rgb(74, 222, 128)
    private val WARN = Color.rgb(251, 191, 36)
    private val BAD = Color.rgb(248, 113, 113)

    private var d = 1f
    private fun dp(v: Int) = (v * d).toInt()
    private fun round(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(color)
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun gradient(r: Int, vararg colors: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = dp(r).toFloat() }

    private fun text(t: String, size: Float, color: Int = TEXT, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-black", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        LangModels.init(this)
        d = resources.displayMetrics.density
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(36))
        }

        // ----- Cabecera -----
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("ES", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACCENT, ACCENT2)).apply {
                shape = GradientDrawable.OVAL
            }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        titles.addView(text("PanelPop", 26f, TEXT, true))
        titles.addView(text("Traductor flotante de cómics", 13f, MUTED))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusPill = text("", 12f, TEXT, true).apply { setPadding(dp(12), dp(6), dp(12), dp(6)) }
        header.addView(statusPill)
        root.addView(header)

        root.addView(text("Abre tu app de mangas, manhwas o cómics y lee los globos en español, directamente encima.", 15f, MUTED),
            lp(top = 18))

        // ----- Botón principal -----
        mainBtn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
            isClickable = true; isFocusable = true
            setOnClickListener { onMain() }
            elevation = dp(6).toFloat()
        }
        mainTitle = text("", 21f, Color.WHITE, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.04f }
        mainSub = text("", 13f, Color.argb(220, 255, 255, 255)).apply { gravity = Gravity.CENTER }
        mainBtn.addView(mainTitle); mainBtn.addView(mainSub, lp(top = 4))
        root.addView(mainBtn, lp(top = 22))

        // ----- Permisos -----
        val pc = card(root, "Permisos")
        permOverlay = PermRow(pc, "Mostrar sobre otras apps", "Para dibujar las traducciones y la burbuja") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        permNotif = PermRow(pc, "Notificaciones", "Para pausar o cerrar desde la barra superior") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }

        // ----- Idioma -----
        val lc = card(root, "Idioma del cómic")
        val langs = listOf("auto" to "Automático", "en" to "Inglés", "ja" to "Japonés", "ko" to "Coreano", "zh" to "Chino")
        lc.addView(chips(langs, prefs.sourceLang) { pick ->
            prefs.sourceLang = pick
            // Al elegir un idioma concreto se descarga ya, para no esperar luego dentro del cómic
            if (pick != "auto" && LangModels.pairState(pick) != LangModels.State.READY) {
                if (LangModels.ensurePair(pick)) toast("Descargando ${LangPick.name(pick)}…")
                else toast("Sin conexión a internet")
            }
        }, lp(top = 4))
        lc.addView(hint("«Automático» reconoce inglés, japonés, coreano y chino. Si sabes el idioma, elígelo: lee más rápido."), lp(top = 10))

        // ----- Idiomas descargados -----
        val mc = card(root, "Idiomas descargados")
        mc.addView(hint("Cada idioma se descarga una sola vez (~30 MB) y luego traduce sin internet. Mejor con Wi‑Fi."), lp(top = 4))
        for (code in listOf("es", "en", "ja", "ko", "zh")) langRows.add(LangRow(mc, code))
        LangModels.addListener(modelListener)

        // ----- Texto -----
        val tc = card(root, "Cómo se ve la traducción")
        tc.addView(chips(listOf("cover" to "Cubrir original", "label" to "Etiqueta debajo"),
            if (prefs.coverMode) "cover" else "label") { prefs.coverMode = it == "cover" }, lp(top = 4))
        slider(tc, "Tamaño de letra", 14, ((prefs.fontScale - 0.6f) * 10).toInt(), { "${60 + it * 10} %" }) {
            prefs.fontScale = 0.6f + it / 10f
        }
        slider(tc, "Opacidad del fondo", 100, prefs.bgAlpha * 100 / 255, { "$it %" }) {
            prefs.bgAlpha = it * 255 / 100
        }
        slider(tc, "Posición vertical", 100, prefs.offsetY + 50, { v -> val o = v - 50; if (o > 0) "+$o" else "$o" }) {
            prefs.offsetY = it - 50
        }

        // ----- Rendimiento -----
        val rc = card(root, "Rendimiento")
        slider(rc, "Esperar al dejar de desplazar", 28, ((prefs.settleSec - 0.2f) * 10).toInt(),
            { String.format("%.1f s", 0.2f + it / 10f) }) { prefs.settleSec = 0.2f + it / 10f }
        rc.addView(hint("Al desplazar, las traducciones se esconden y aparecen las nuevas cuando paras."), lp(top = 2))
        rc.addView(text("Calidad de lectura", 14f, TEXT), lp(top = 16))
        rc.addView(chips(listOf("0" to "Ahorro", "1" to "Equilibrada", "2" to "Alta"), prefs.performance.toString()) {
            prefs.performance = it.toInt()
        }, lp(top = 8))
        rc.addView(hint("La calidad se aplica la próxima vez que actives el traductor."), lp(top = 10))

        // ----- Ayuda -----
        val hc = card(root, "La burbuja flotante")
        for ((k, v) in listOf("Toca" to "pausar o reanudar", "Arrastra" to "moverla a otro lado", "Mantén pulsado" to "cerrar el traductor")) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(k, 13f, ACCENT, true).apply {
                setPadding(dp(10), dp(4), dp(10), dp(4)); background = round(CARD_HI, 8)
            })
            row.addView(text(v, 14f, TEXT).apply { setPadding(dp(12), 0, 0, 0) })
            hc.addView(row, lp(top = 8))
        }

        root.addView(text("Sin anuncios ni suscripciones. Las capturas solo viven en memoria y nunca se guardan.", 12f, MUTED).apply {
            gravity = Gravity.CENTER
        }, lp(top = 24))

        val translatorPage = ScrollView(this).apply {
            setBackgroundColor(BG); isVerticalScrollBarEnabled = false
            addView(root)
        }
        buildTabs(translatorPage)
        if (Build.VERSION.SDK_INT >= 33 && !notifGranted())
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    // ---------- pestañas (Biblioteca · Explorar · Traductor), como Mihon ----------
    private lateinit var libraryPage: LibraryPage
    private lateinit var explorePage: ExplorePage
    private val pages = ArrayList<View>()
    private val tabViews = ArrayList<Pair<TextView, TextView>>()
    private var tab = 0

    private fun buildTabs(translatorPage: View) {
        val ui = Ui(this)
        Library.init(this)
        libraryPage = LibraryPage(this, ui)
        explorePage = ExplorePage(this, ui)
        val host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val frame = android.widget.FrameLayout(this)
        pages.clear(); pages.addAll(listOf(libraryPage.view, explorePage.view, translatorPage))
        for (p in pages) frame.addView(p, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        host.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(CARD); setPadding(0, dp(8), 0, dp(10))
        }
        listOf("▦" to "Biblioteca", "◎" to "Explorar", "文A" to "Traductor").forEachIndexed { i, (icon, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true
                setOnClickListener { selectTab(i) }
            }
            val ic = text(icon, 17f, MUTED, true).apply { gravity = Gravity.CENTER }
            item.addView(ic, LinearLayout.LayoutParams(dp(64), dp(32)))
            val lb = text(label, 12f, MUTED).apply { gravity = Gravity.CENTER }
            item.addView(lb, lp(ViewGroup.LayoutParams.WRAP_CONTENT, top = 4))
            nav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            tabViews.add(ic to lb)
        }
        host.addView(nav)
        window.navigationBarColor = CARD
        setContentView(host)
        selectTab(getSharedPreferences("panelpop", MODE_PRIVATE).getInt("tab", 0).coerceIn(0, 2))
    }

    private fun selectTab(i: Int) {
        tab = i
        getSharedPreferences("panelpop", MODE_PRIVATE).edit().putInt("tab", i).apply()
        pages.forEachIndexed { j, v -> v.visibility = if (j == i) View.VISIBLE else View.GONE }
        tabViews.forEachIndexed { j, (ic, lb) ->
            val on = j == i
            ic.background = if (on) round(Color.argb(60, 255, 106, 61), 16) else null
            ic.setTextColor(if (on) ACCENT else MUTED)
            lb.setTextColor(if (on) TEXT else MUTED)
        }
        when (i) { 0 -> libraryPage.refresh(); 1 -> explorePage.refresh() }
    }

    // ---------- piezas de interfaz ----------
    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = round(CARD, 20, LINE)
        }
        c.addView(text(title.uppercase(), 12f, MUTED, true).apply { letterSpacing = 0.1f })
        parent.addView(c, lp(top = 14))
        return c
    }

    private fun hint(t: String) = text(t, 12.5f, MUTED)

    private fun chips(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val views = ArrayList<TextView>()
        fun paint(sel: String) = options.forEachIndexed { i, (key, _) ->
            val on = key == sel
            views[i].background = if (on) gradient(14, ACCENT, ACCENT2) else round(CARD_HI, 14, LINE)
            views[i].setTextColor(if (on) Color.WHITE else TEXT)
        }
        options.forEachIndexed { i, (key, label) ->
            val v = text(label, 14f, TEXT, false).apply {
                setPadding(dp(16), dp(10), dp(16), dp(10))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                isClickable = true
                setOnClickListener { onPick(key); paint(key) }
            }
            views.add(v)
            row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) leftMargin = dp(8)
            })
        }
        paint(selected)
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun slider(parent: LinearLayout, title: String, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(title, 14f, TEXT), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = text(fmt(value.coerceIn(0, max)), 14f, ACCENT, true)
        head.addView(valueView)
        parent.addView(head, lp(top = 16))
        val bar = SeekBar(this).apply {
            this.max = max
            progress = value.coerceIn(0, max)
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(LINE)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    onChange(p); valueView.text = fmt(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        parent.addView(bar, lp(top = 2).apply { leftMargin = -dp(12); rightMargin = -dp(12) })
    }

    /** Fila de permiso con estado y botón para concederlo. */
    private inner class PermRow(parent: LinearLayout, title: String, desc: String, private val onFix: () -> Unit) {
        private val dot = View(this@MainActivity)
        private val btn: TextView
        init {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(12) })
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(title, 15f, TEXT))
            col.addView(text(desc, 12.5f, MUTED))
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            btn = text("Permitir", 13f, Color.WHITE, true).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8)); background = gradient(12, ACCENT, ACCENT2)
                setOnClickListener { onFix() }
            }
            row.addView(btn)
            parent.addView(row, lp(top = 12))
        }
        fun set(ok: Boolean) {
            dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (ok) OK else WARN) }
            btn.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }

    /** Fila de un idioma: estado del modelo y botón Descargar / Reintentar. */
    private inner class LangRow(parent: LinearLayout, private val code: String) {
        private val dot = View(this@MainActivity)
        private val sub = text("", 12.5f, MUTED)
        private val spin = ProgressBar(this@MainActivity).apply {
            isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(ACCENT)
        }
        private val btn = text("Descargar", 13f, Color.WHITE, true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8)); background = gradient(12, ACCENT, ACCENT2)
            setOnClickListener {
                val ok = if (code == "es") LangModels.download(code) else LangModels.ensurePair(code)
                if (!ok) toast("Sin conexión a internet")
                update()
            }
        }
        init {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(12) })
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(LangPick.name(code).replaceFirstChar { it.uppercase() }, 15f, TEXT))
            col.addView(sub)
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(spin, LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(4) })
            row.addView(btn)
            parent.addView(row, lp(top = 12))
            update()
        }
        fun update() {
            val st = if (code == "es" || code == "en") LangModels.state(code) else LangModels.pairState(code)
            val (color, msg) = when {
                !LangModels.checked -> MUTED to "Comprobando…"
                st == LangModels.State.READY -> OK to when (code) {
                    "en" -> "Incluido · funciona sin internet"
                    "es" -> "Listo · necesario para traducir"
                    else -> "Listo · funciona sin internet"
                }
                st == LangModels.State.DOWNLOADING -> WARN to "Descargando… puedes salir de aquí"
                st == LangModels.State.FAILED -> BAD to ((LangModels.error(code) ?: LangModels.error("es") ?: "No se pudo descargar") + " · toca Reintentar")
                else -> MUTED to if (code == "es") "Necesario para traducir" else "No descargado"
            }
            dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            sub.text = msg
            spin.visibility = if (st == LangModels.State.DOWNLOADING) View.VISIBLE else View.GONE
            btn.text = if (st == LangModels.State.FAILED) "Reintentar" else "Descargar"
            btn.visibility = if (LangModels.checked && (st == LangModels.State.MISSING || st == LangModels.State.FAILED)) View.VISIBLE else View.GONE
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- lógica ----------
    private fun notifGranted() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume(); refresh()
        LangModels.refresh { langRows.forEach { it.update() } }
    }

    override fun onDestroy() { LangModels.removeListener(modelListener); super.onDestroy() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        if (requestCode == REQ_NOTIF && !notifGranted() && Build.VERSION.SDK_INT >= 33 &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            // El sistema ya no vuelve a preguntar: abre los ajustes de la app
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        permOverlay.set(overlay)
        permNotif.set(notifGranted())
        val on = TranslatorService.running
        statusPill.text = if (on) "● ACTIVO" else "○ APAGADO"
        statusPill.setTextColor(if (on) OK else MUTED)
        statusPill.background = round(if (on) Color.argb(40, 74, 222, 128) else CARD_HI, 20)
        when {
            on -> {
                mainBtn.background = round(CARD_HI, 26, ACCENT)
                mainTitle.text = "DETENER TRADUCTOR"
                mainSub.text = "El traductor está funcionando sobre tus apps"
            }
            !overlay -> {
                mainBtn.background = gradient(26, ACCENT, ACCENT2)
                mainTitle.text = "PERMITIR SUPERPOSICIÓN"
                mainSub.text = "Paso 1: activa «Mostrar sobre otras apps»"
            }
            else -> {
                mainBtn.background = gradient(26, ACCENT, ACCENT2)
                mainTitle.text = "ACTIVAR TRADUCTOR"
                mainSub.text = "Luego abre tu app de cómics"
            }
        }
    }

    private fun onMain() {
        if (TranslatorService.running) {
            startService(Intent(this, TranslatorService::class.java).setAction(TranslatorService.ACTION_STOP))
            mainBtn.postDelayed({ refresh() }, 400); return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Android 14+: pide la pantalla completa (no una sola app) para que las posiciones coincidan
        val intent = if (Build.VERSION.SDK_INT >= 34)
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
        startActivityForResult(intent, REQ_CAPTURE)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_CAPTURE) return
        if (res != RESULT_OK || data == null) {
            Toast.makeText(this, "Sin permiso de captura no se puede traducir", Toast.LENGTH_SHORT).show(); return
        }
        startForegroundService(Intent(this, TranslatorService::class.java)
            .putExtra(TranslatorService.EXTRA_CODE, res).putExtra(TranslatorService.EXTRA_DATA, data))
        Toast.makeText(this, "Traductor activado · abre tu app de cómics", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
