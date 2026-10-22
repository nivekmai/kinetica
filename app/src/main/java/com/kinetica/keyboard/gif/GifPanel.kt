package com.kinetica.keyboard.gif

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kinetica.keyboard.R
import com.kinetica.keyboard.ui.KeyboardTheme

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
        row.addView(button(R.string.gif_search, onSearch), LayoutParams(0, -2, 2f))
        row.addView(button(R.string.emoji_back_to_letters, onClose), LayoutParams(0, -2, 1f))
        addView(row)
        addView(TextView(context).apply {
            setText(if (!supported) R.string.gif_unsupported else if (pending != null) R.string.gif_ready else R.string.gif_recents)
            setTextColor(theme.keyText); setPadding(dp(8), dp(6), dp(8), dp(6))
        })
        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        val ids = (listOfNotNull(pending) + recents).distinct()
        if (ids.isEmpty()) grid.addView(TextView(context).apply {
            setText(R.string.gif_empty); setTextColor(theme.keyHint); setPadding(dp(12), dp(12), dp(12), dp(12))
        })
        val store = GifStore(context)
        val confirmation = LinearLayout(context).apply { orientation = VERTICAL; visibility = GONE }
        val scroll = ScrollView(context).apply { addView(grid) }
        fun confirmRemoval(id: String) {
            confirmation.removeAllViews()
            confirmation.addView(TextView(context).apply {
                setText(R.string.gif_remove_confirm); setTextColor(theme.keyText)
                textSize = 18f; setPadding(dp(16), dp(16), dp(16), dp(16))
                accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
            })
            val actions = LinearLayout(context)
            val cancel = button(android.R.string.cancel) {
                confirmation.visibility = GONE
                scroll.visibility = VISIBLE
            }
            actions.addView(cancel, LayoutParams(0, -2, 1f))
            actions.addView(button(R.string.gif_remove) { onRemove(id) }, LayoutParams(0, -2, 1f))
            confirmation.addView(actions)
            scroll.visibility = GONE
            confirmation.visibility = VISIBLE
            cancel.requestFocus()
        }
        ids.chunked(3).forEach { group ->
            val cells = LinearLayout(context)
            group.forEach { id ->
                store.file(id)?.let { file ->
                    val cell = LinearLayout(context).apply { orientation = VERTICAL }
                    cell.addView(GifThumbnail(context, file), LayoutParams(-1, -1))
                    cell.contentDescription = context.getString(R.string.gif_tile_description, ids.indexOf(id) + 1)
                    cell.alpha = if (supported) 1f else .5f
                    cell.isFocusable = true
                    cell.setOnClickListener { if (supported) onSend(id) }
                    // Return true so releasing a long press never also attaches the GIF.
                    cell.setOnLongClickListener { confirmRemoval(id); true }
                    cells.addView(cell, LayoutParams(0, dp(100), 1f))
                }
            }
            repeat(3 - group.size) { cells.addView(TextView(context), LayoutParams(0, 0, 1f)) }
            grid.addView(cells)
        }
        addView(scroll, LayoutParams(-1, 0, 1f))
        addView(confirmation, LayoutParams(-1, 0, 1f))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
