package com.kinetica.keyboard.gif

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.kinetica.keyboard.engine.KineticaConstants

/** Shared rounded action surface, including feedback when pressed or keyboard-focused. */
object GifActionStyle {
    fun apply(view: View, background: Int, foreground: Int) {
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = KineticaConstants.GIF_ACTION_RADIUS_DP * view.resources.displayMetrics.density
        }
        view.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(foreground, 48)),
            shape(background), shape(Color.WHITE),
        )
    }
}
