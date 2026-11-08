package app.panelpop

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Pestaña Biblioteca: portadas de las series guardadas con los capítulos sin leer. */
class LibraryPage(private val a: Activity, private val ui: Ui) {
    private val adapter = CoverAdapter(ui, emptyList())
    private val emptyView = ui.empty("Tu biblioteca está vacía",
        "Ve a Explorar, añade tu web de mangas como repositorio y guarda tus series con ♡.")
    private val count: TextView
    val view: View

    init {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        count = ui.text("", 13f, Pal.MUTED, true)
        val (bar, _) = ui.topBar("Biblioteca", null, count)
        col.addView(bar)
        val body = FrameLayout(a)
        body.addView(ui.coverGrid(adapter, { open(it) }, { askRemove(it) }))
        body.addView(emptyView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        col.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view = col
    }

    fun refresh() {
        val list = Library.library()
        adapter.items = list.map { CoverItem(it.url, it.title, it.cover, it.url, it.unread) }
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        count.text = if (list.isEmpty()) "" else "${list.size}"
    }

    private fun open(c: CoverItem) = a.startActivity(Intent(a, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_URL, c.url))

    private fun askRemove(c: CoverItem) {
        AlertDialog.Builder(a).setTitle(c.title).setMessage("¿Quitar de la biblioteca?")
            .setPositiveButton("Quitar") { _, _ -> Library.get(c.url)?.let { Library.setInLibrary(it, false) }; refresh() }
            .setNegativeButton("Cancelar", null).show()
    }
}

/** Pestaña Explorar: las webs del usuario funcionan como repositorios (fuentes de Mihon). */
class ExplorePage(private val a: Activity, private val ui: Ui) {
    private val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    val view: View

    init {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ui.topBar("Explorar", null,
            ui.iconAction(Icons.SEARCH) { searchAll() }, ui.iconAction(Icons.ADD) { askAdd() }).first)
        val inner = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(24)) }
        inner.addView(ui.button("Buscar en todas las webs") { searchAll() }, ui.lp(top = 4))
        inner.addView(ui.text("Escribe un nombre y PanelPop lo buscará en todos tus repositorios a la vez.", 13f, Pal.MUTED), ui.lp(top = 8))
        inner.addView(ui.text("REPOSITORIOS", 12f, Pal.MUTED, true).apply { letterSpacing = 0.1f }, ui.lp(top = 22))
        inner.addView(list, ui.lp(top = 6))
        inner.addView(ui.button("+  Añadir repositorio") { askAdd() }, ui.lp(top = 14))
        inner.addView(ui.text("Cualquier web de mangas, manhwas o cómics sirve como repositorio: PanelPop lee sus series, " +
            "capítulos y páginas, y las abre en su propio lector sin anuncios. Mantén pulsado un repositorio para quitarlo.", 13f, Pal.MUTED), ui.lp(top = 14))
        col.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(inner) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view = col
    }

    fun refresh() {
        list.removeAllViews()
        if (Library.repos.isEmpty()) {
            list.addView(ui.text("Aún no tienes repositorios. Pulsa «Añadir repositorio» y escribe la dirección de tu web.", 14f, Pal.TEXT).apply {
                setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16)); background = ui.round(Pal.CARD, 16, Pal.LINE)
            }, ui.lp(top = 6))
            return
        }
        for (r in Library.repos) list.addView(row(r), ui.lp(top = 8))
    }

    private fun row(r: Library.Repo): View {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12)); background = ui.round(Pal.CARD, 16, Pal.LINE)
            isClickable = true
            setOnClickListener { a.startActivity(Intent(a, SourceActivity::class.java).putExtra(SourceActivity.EXTRA_URL, r.url).putExtra(SourceActivity.EXTRA_NAME, r.name)) }
            setOnLongClickListener {
                AlertDialog.Builder(a).setTitle(r.name).setMessage("¿Quitar este repositorio? Tus series guardadas no se borran.")
                    .setPositiveButton("Quitar") { _, _ -> Library.removeRepo(r); refresh() }
                    .setNegativeButton("Cancelar", null).show(); true
            }
        }
        val initial = r.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        row.addView(ui.text(initial, 17f, android.graphics.Color.WHITE, true).apply { gravity = Gravity.CENTER; background = ui.gradient(12) },
            LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)))
        val t = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(14), 0, 0, 0) }
        t.addView(ui.text(r.name, 16f, Pal.TEXT, true))
        t.addView(ui.text(WebParse.host(r.url), 12.5f, Pal.MUTED))
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ui.text("Explorar ›", 13f, Pal.ACCENT, true))
        return row
    }

    fun askAdd() {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0) }
        val url = EditText(a).apply { hint = "Dirección, p. ej. miweb.com"; inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT; isSingleLine = true }
        val name = EditText(a).apply { hint = "Nombre (opcional)"; isSingleLine = true }
        box.addView(url); box.addView(name)
        AlertDialog.Builder(a).setTitle("Añadir repositorio").setView(box)
            .setPositiveButton("Añadir") { _, _ ->
                val u = WebParse.normalizeUrl(url.text.toString())
                when {
                    u == null -> Toast.makeText(a, "Esa dirección no parece una web", Toast.LENGTH_SHORT).show()
                    !Library.addRepo(u, name.text.toString().trim()) -> Toast.makeText(a, "Ese repositorio ya está añadido", Toast.LENGTH_SHORT).show()
                    else -> refresh()
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }
}
