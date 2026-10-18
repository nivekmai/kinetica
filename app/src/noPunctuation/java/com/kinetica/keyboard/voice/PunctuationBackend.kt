package com.kinetica.keyboard.voice

import android.content.Context

/** The normal source build can still run without the optional native dependency. */
class PunctuationBackend(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun punctuate(@Suppress("UNUSED_PARAMETER") text: String): String = error("Punctuation model not bundled")
    fun close() = Unit
    companion object { const val bundled = false }
}
