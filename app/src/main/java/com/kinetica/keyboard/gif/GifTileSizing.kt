package com.kinetica.keyboard.gif

import com.kinetica.keyboard.engine.KineticaConstants

/** GIF canvas dimensions can be read without decoding a bitmap. */
object GifTileSizing {
    fun dimensions(header: ByteArray): Pair<Int, Int>? {
        if (header.size < 10 || String(header, 0, 6, Charsets.US_ASCII) !in setOf("GIF87a", "GIF89a")) return null
        fun word(i: Int) = (header[i].toInt() and 255) or ((header[i + 1].toInt() and 255) shl 8)
        val width = word(6)
        val height = word(8)
        return if (width in 1..KineticaConstants.GIF_MAX_DIMENSION && height in 1..KineticaConstants.GIF_MAX_DIMENSION)
            width to height else null
    }

    fun height(columnWidth: Int, imageWidth: Int, imageHeight: Int, minHeight: Int): Int {
        val width = columnWidth.coerceAtLeast(1)
        val natural = if (imageWidth > 0 && imageHeight > 0)
            (width.toLong() * imageHeight / imageWidth).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else width
        val minimum = minHeight.coerceAtLeast(1)
        val maximum = (width.toLong() * KineticaConstants.GIF_TILE_MAX_HEIGHT_RATIO)
            .coerceIn(minimum.toLong(), Int.MAX_VALUE.toLong()).toInt()
        return natural.coerceIn(minimum, maximum)
    }
}
