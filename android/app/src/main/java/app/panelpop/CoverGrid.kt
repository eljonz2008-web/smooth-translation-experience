package app.panelpop

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

/** Una portada de la cuadrícula (biblioteca o repositorio). */
class CoverItem(val url: String, val title: String, val image: String, val referer: String, val badge: Int = 0)

/** Cuadrícula de portadas de 3 columnas, como la biblioteca de Mihon. */
class CoverAdapter(private val ui: Ui, var items: List<CoverItem>) : BaseAdapter() {
    private val cols = 3
    override fun getCount() = items.size
    override fun getItem(p: Int) = items[p]
    override fun getItemId(p: Int) = p.toLong()

    private class Holder(val img: ImageView, val title: TextView, val badge: TextView)

    override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
        val cell = (convert as? FrameLayout) ?: build()
        val h = cell.tag as Holder
        val it = items[p]
        val screenW = ui.ctx.resources.displayMetrics.widthPixels
        val w = (screenW - ui.dp(12) * 2 - ui.dp(10) * (cols - 1)) / cols
        cell.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (w * 1.5f).toInt())
        h.title.text = it.title
        h.badge.text = it.badge.toString()
        h.badge.visibility = if (it.badge > 0) View.VISIBLE else View.GONE
        ImageLoader.load(h.img, it.image, it.referer)
        return cell
    }

    private fun build(): FrameLayout {
        val c = ui.ctx
        val cell = FrameLayout(c).apply {
            background = ui.round(Pal.CARD, 10)
            clipToOutline = true
        }
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        cell.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val shade = View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.argb(230, 0, 0, 0), Color.TRANSPARENT))
        }
        cell.addView(shade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(80), Gravity.BOTTOM))
        val title = ui.text("", 12.5f, Color.WHITE, false).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            setPadding(ui.dp(8), 0, ui.dp(8), ui.dp(7))
        }
        cell.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        val badge = ui.text("", 11.5f, Color.WHITE, true).apply {
            background = ui.gradient(6); setPadding(ui.dp(6), ui.dp(2), ui.dp(6), ui.dp(2))
        }
        cell.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
            .apply { setMargins(ui.dp(6), ui.dp(6), 0, 0) })
        cell.tag = Holder(img, title, badge)
        return cell
    }
}

fun Ui.coverGrid(adapter: CoverAdapter, onClick: (CoverItem) -> Unit, onLong: ((CoverItem) -> Unit)? = null) = GridView(ctx).apply {
    numColumns = 3
    horizontalSpacing = dp(10); verticalSpacing = dp(10)
    setPadding(dp(12), dp(4), dp(12), dp(16)); clipToPadding = false
    stretchMode = GridView.STRETCH_COLUMN_WIDTH
    selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    this.adapter = adapter
    setOnItemClickListener { _, _, p, _ -> onClick(adapter.items[p]) }
    if (onLong != null) setOnItemLongClickListener { _, _, p, _ -> onLong(adapter.items[p]); true }
}
