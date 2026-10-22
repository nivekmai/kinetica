package com.kinetica.keyboard.gif

import android.content.Context
import android.content.res.ColorStateList
import androidx.appcompat.widget.AppCompatImageButton
import com.kinetica.keyboard.R

/** Shared icon and spoken label for opening search and submitting a query. */
class GifSearchButton(context: Context, onSearch: () -> Unit) : AppCompatImageButton(context) {
    init {
        setImageResource(R.drawable.ic_gif_search)
        contentDescription = context.getString(R.string.gif_search_go)
        tooltipText = contentDescription
        val padding = (12 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        val color = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, color, true)
        imageTintList = if (color.resourceId != 0) context.getColorStateList(color.resourceId) else ColorStateList.valueOf(color.data)
        setOnClickListener { onSearch() }
    }
}
