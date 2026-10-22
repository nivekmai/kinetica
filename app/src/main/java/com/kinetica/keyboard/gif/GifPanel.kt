package com.kinetica.keyboard.gif

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.kinetica.keyboard.R
import com.kinetica.keyboard.engine.KineticaConstants
import com.kinetica.keyboard.ui.KeyboardTheme
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Search stays online; accepted attachments are kept locally for quick reuse. */
class GifPanel(
    context: Context,
    theme: KeyboardTheme,
    pending: String?,
    recents: List<String>,
    supported: Boolean,
    onSearch: () -> Unit,
    onSend: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
) : LinearLayout(context) {
    private var loadMetadata: (() -> Unit)? = null
    private var metadataWork: Future<*>? = null
    private var generation = 0
    private var loaded = false

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val row = LinearLayout(context)
        fun button(text: Int, callback: () -> Unit) = TextView(context).apply {
            setText(text); setTextColor(theme.keyText); setBackgroundColor(theme.keySpecial)
            gravity = Gravity.CENTER; textSize = 15f
            setPadding(8, 12, 8, 12); minimumHeight = dp(48)
            isClickable = true; isFocusable = true
            setOnClickListener { callback() }
        }
        row.setBackgroundColor(theme.keySpecial)
        row.addView(GifSearchButton(context, onSearch).apply {
            imageTintList = ColorStateList.valueOf(theme.keyText)
            setBackgroundColor(theme.keySpecial)
        }, LayoutParams(dp(48), dp(48)))
        row.addView(android.view.View(context), LayoutParams(0, 1, 1f))
        row.addView(button(R.string.emoji_back_to_letters, onClose), LayoutParams(-2, dp(48)))
        addView(row)
        addView(TextView(context).apply {
            setText(if (!supported) R.string.gif_unsupported else if (pending != null) R.string.gif_ready else R.string.gif_recents)
            setTextColor(theme.keyText); setPadding(dp(8), dp(6), dp(8), dp(6))
        })
        val body = FrameLayout(context)
        val grid = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(KineticaConstants.GIF_GRID_COLUMNS, RecyclerView.VERTICAL)
            itemAnimator = null
            clipToPadding = false
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val empty = TextView(context).apply {
            setText(R.string.gif_loading); setTextColor(theme.keyHint)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val confirmation = LinearLayout(context).apply { orientation = VERTICAL }
        val confirmationScroll = ScrollView(context).apply { addView(confirmation); visibility = GONE }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        body.addView(empty, FrameLayout.LayoutParams(-1, -2))
        body.addView(confirmationScroll, FrameLayout.LayoutParams(-1, -1))
        addView(body, LayoutParams(-1, 0, 1f))

        fun confirmRemoval(id: String) {
            confirmation.removeAllViews()
            confirmation.addView(TextView(context).apply {
                setText(R.string.gif_remove_confirm); setTextColor(theme.keyText)
                textSize = 18f; setPadding(dp(16), dp(16), dp(16), dp(16))
                accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
            })
            val actions = LinearLayout(context)
            val cancel = button(android.R.string.cancel) {
                confirmationScroll.visibility = GONE
                grid.visibility = VISIBLE
            }
            actions.addView(cancel, LayoutParams(0, -2, 1f))
            actions.addView(button(R.string.gif_remove) { onRemove(id) }, LayoutParams(0, -2, 1f))
            confirmation.addView(actions)
            grid.visibility = GONE
            confirmationScroll.visibility = VISIBLE
            cancel.requestFocus()
        }
        val adapter = RecentAdapter(supported, onSend, ::confirmRemoval)
        grid.adapter = adapter
        val ids = (listOfNotNull(pending) + recents).distinct()
        // Read the tiny headers together before displaying cards, so asynchronous bitmap
        // decoding cannot change tile heights and move a GIF out from under a user's finger.
        loadMetadata = {
            val ticket = ++generation
            metadataWork = metadataExecutor.submit {
                val store = GifStore(context)
                val entries = ids.mapNotNull { id ->
                    runCatching {
                        val file = store.file(id) ?: return@runCatching null
                        val size = file.inputStream().use { input ->
                            val header = ByteArray(10)
                            java.io.DataInputStream(input).readFully(header)
                            GifTileSizing.dimensions(header)
                        }
                            ?: return@runCatching null
                        Recent(id, file, size.first, size.second)
                    }.getOrNull()
                }
                post {
                    if (ticket == generation && isAttachedToWindow) {
                        loaded = true
                        adapter.entries = entries
                        adapter.notifyDataSetChanged()
                        empty.setText(R.string.gif_empty)
                        empty.visibility = if (entries.isEmpty()) VISIBLE else GONE
                    }
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!loaded) loadMetadata?.invoke()
    }

    override fun onDetachedFromWindow() {
        generation++
        metadataWork?.cancel(true)
        super.onDetachedFromWindow()
    }

    private data class Recent(val id: String, val file: File, val width: Int, val height: Int)
    private class Holder(val image: GifThumbnail) : RecyclerView.ViewHolder(image)
    private inner class RecentAdapter(
        private val supported: Boolean,
        private val onSend: (String) -> Unit,
        private val onRemove: (String) -> Unit,
    ) : RecyclerView.Adapter<Holder>() {
        var entries: List<Recent> = emptyList()
        override fun getItemCount() = entries.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(GifThumbnail(parent.context, fitAspect = true).apply {
                layoutParams = StaggeredGridLayoutManager.LayoutParams(-1, -2).apply {
                    val gap = dp(KineticaConstants.GIF_TILE_GAP_DP)
                    setMargins(gap, gap, gap, gap)
                }
                isFocusable = true
            })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = entries[position]
            holder.image.apply {
                bind(entry.file, entry.width, entry.height)
                contentDescription = context.getString(R.string.gif_tile_description, position + 1)
                alpha = if (supported) 1f else .5f
                setOnClickListener { if (supported) onSend(entry.id) }
                setOnLongClickListener { onRemove(entry.id); true }
            }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private val metadataExecutor = Executors.newSingleThreadExecutor { Thread(it, "gif-metadata").apply { isDaemon = true } }
    }
}
