package app.panelpop

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Biblioteca y repositorios guardados en el teléfono (un archivo JSON, sin cuentas ni servidores).
 * También recuerda las series visitadas que no están en la biblioteca, para no perder lo leído.
 */
object Library {
    /** [search]: cómo busca esa web (ver [WebParse.searchUrl]); vacío = aún no se sabe. */
    data class Repo(val url: String, var name: String, var search: String = "")

    class Series(
        val url: String,
        var title: String,
        var cover: String,
        var chapters: List<WebParse.Chapter> = emptyList(),
        val read: MutableSet<String> = HashSet(),
        var lastRead: String? = null,
        var inLibrary: Boolean = false,
        var addedAt: Long = 0L,
        var readAt: Long = 0L,
        var desc: String = "",
        var genres: List<String> = emptyList(),
    ) {
        val unread get() = WebParse.unread(chapters, read)
        val source get() = WebParse.host(url)
    }

    private var file: File? = null
    private val series = LinkedHashMap<String, Series>()
    val repos = ArrayList<Repo>()

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "library.json")
        file = f
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            o.optJSONArray("repos")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { repos.add(Repo(it.getString("url"), it.optString("name"), it.optString("search"))) } }
            o.optJSONArray("series")?.let { a ->
                for (i in 0 until a.length()) {
                    val s = a.getJSONObject(i)
                    val chs = ArrayList<WebParse.Chapter>()
                    s.optJSONArray("ch")?.let { c -> for (j in 0 until c.length()) c.getJSONObject(j).let {
                        chs.add(WebParse.Chapter(it.getString("u"), it.optString("n"), it.optDouble("x", 0.0), it.optString("d")))
                    } }
                    val read = HashSet<String>()
                    s.optJSONArray("read")?.let { r -> for (j in 0 until r.length()) read.add(r.getString(j)) }
                    val ser = Series(s.getString("url"), s.optString("title"), s.optString("cover"), chs, read,
                        s.optString("last").ifEmpty { null }, s.optBoolean("lib"), s.optLong("added"), s.optLong("readAt"),
                        s.optString("desc"), s.optJSONArray("gen")?.let { g -> (0 until g.length()).map { g.optString(it) } } ?: emptyList())
                    series[WebParse.key(ser.url)] = ser
                }
            }
        } catch (e: Exception) { /* archivo dañado: empezamos de cero */ }
    }

    @Synchronized fun save() {
        val f = file ?: return
        val o = JSONObject()
        o.put("repos", JSONArray().apply { repos.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("search", it.search)) } })
        o.put("series", JSONArray().apply {
            // Solo guardamos las de la biblioteca o las que tienen algo leído
            series.values.filter { it.inLibrary || it.read.isNotEmpty() }.forEach { s ->
                put(JSONObject().put("url", s.url).put("title", s.title).put("cover", s.cover)
                    .put("lib", s.inLibrary).put("added", s.addedAt).put("readAt", s.readAt).put("last", s.lastRead ?: "")
                    .put("read", JSONArray(s.read.toList())).put("desc", s.desc).put("gen", JSONArray(s.genres))
                    .put("ch", JSONArray().apply { s.chapters.forEach { put(JSONObject().put("u", it.url).put("n", it.name).put("x", it.number).put("d", it.date)) } }))
            }
        })
        val tmp = File(f.parentFile, "library.json.tmp")
        tmp.writeText(o.toString()); tmp.renameTo(f)
    }

    fun get(url: String): Series? = series[WebParse.key(url)]

    fun obtain(url: String, title: String = "", cover: String = ""): Series =
        series.getOrPut(WebParse.key(url)) { Series(url, title.ifEmpty { WebParse.slugTitle(url) }, cover) }

    fun library(): List<Series> = series.values.filter { it.inLibrary }.sortedByDescending { maxOf(it.readAt, it.addedAt) }

    fun setInLibrary(s: Series, on: Boolean) {
        s.inLibrary = on; if (on && s.addedAt == 0L) s.addedAt = System.currentTimeMillis(); save()
    }

    fun markRead(s: Series, chapterUrl: String) {
        s.read.add(WebParse.key(chapterUrl)); s.lastRead = chapterUrl; s.readAt = System.currentTimeMillis(); save()
    }

    fun addRepo(url: String, name: String, search: String = ""): Boolean {
        if (hasRepo(url)) return false
        repos.add(Repo(url, name.ifEmpty { WebParse.host(url) }, search)); save(); return true
    }

    fun hasRepo(url: String) = repos.any { WebParse.host(it.url) == WebParse.host(url) }

    fun repoFor(url: String) = repos.firstOrNull { WebParse.sameSite(it.url, url) }

    fun removeRepo(r: Repo) { repos.remove(r); save() }
}
