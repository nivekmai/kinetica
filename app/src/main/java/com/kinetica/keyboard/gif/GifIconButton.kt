package com.kinetica.keyboard.gif

import android.content.Context
import android.content.res.ColorStateList
import androidx.appcompat.widget.AppCompatImageButton
import com.kinetica.keyboard.R
import com.kinetica.keyboard.engine.KineticaConstants

/** Icon actions with spoken labels, tooltips and a rounded touch surface. */
class GifIconButton(
    context: Context,
    icon: Int = R.drawable.ic_gif_search,
    label: Int = R.string.gif_search_go,
    onClick: () -> Unit,
) : AppCompatImageButton(context) {
    init {
        setImageResource(icon)
        contentDescription = context.getString(label)
        tooltipText = contentDescription
        val padding = (KineticaConstants.GIF_ICON_PADDING_DP * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        val color = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, color, true)
        imageTintList = if (color.resourceId != 0) context.getColorStateList(color.resourceId) else ColorStateList.valueOf(color.data)
        val foreground = imageTintList!!.defaultColor
        context.theme.resolveAttribute(android.R.attr.colorButtonNormal, color, true)
        GifActionStyle.apply(this, color.data, foreground)
        setOnClickListener { onClick() }
    }
}
