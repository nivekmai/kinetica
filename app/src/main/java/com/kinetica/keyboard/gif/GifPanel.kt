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
    onClear: () -> Unit,
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
        row.addView(button(R.string.gif_clear, onClear), LayoutParams(0, -2, 1f))
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
        ids.chunked(3).forEach { group ->
            val cells = LinearLayout(context)
            group.forEach { id ->
                store.file(id)?.let { file ->
                    val cell = LinearLayout(context).apply { orientation = VERTICAL }
                    cell.addView(GifThumbnail(context, file), LayoutParams(-1, dp(90)))
                    cell.addView(TextView(context).apply {
                        setText(if (id == pending) R.string.gif_insert else R.string.gif_insert_recent)
                        setTextColor(theme.keyText); gravity = Gravity.CENTER
                    })
                    cell.contentDescription = context.getString(R.string.gif_insert)
                    cell.isEnabled = supported; cell.alpha = if (supported) 1f else .5f
                    cell.isFocusable = true
                    cell.setOnClickListener { onSend(id) }
                    cells.addView(cell, LayoutParams(0, dp(120), 1f))
                }
            }
            repeat(3 - group.size) { cells.addView(TextView(context), LayoutParams(0, 0, 1f)) }
            grid.addView(cells)
        }
        addView(ScrollView(context).apply { addView(grid) }, LayoutParams(-1, 0, 1f))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
