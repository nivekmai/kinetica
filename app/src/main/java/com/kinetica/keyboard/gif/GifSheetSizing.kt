package com.kinetica.keyboard.gif

/** Never allow a large portrait keyboard height to cover the query in a small window. */
object GifSheetSizing {
    fun height(preferredPx: Int, availablePx: Int): Int =
        preferredPx.coerceAtLeast(1).coerceAtMost(availablePx.coerceAtLeast(1))
}
