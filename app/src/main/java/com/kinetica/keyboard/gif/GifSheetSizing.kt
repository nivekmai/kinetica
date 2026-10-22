package com.kinetica.keyboard.gif

import com.kinetica.keyboard.engine.KineticaConstants

/** Never allow a large portrait keyboard height to cover the query in a small window. */
object GifSheetSizing {
    fun height(keyboardPx: Int, availablePx: Int): Int =
        (keyboardPx.toDouble() * KineticaConstants.GIF_SHEET_HEIGHT_RATIO).toInt()
            .coerceAtLeast(1).coerceAtMost(availablePx.coerceAtLeast(1))
}
