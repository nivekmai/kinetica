package com.kinetica.keyboard.gif

import com.kinetica.keyboard.engine.KineticaConstants
import java.net.URI
import java.net.URLEncoder

/** No editor text is used to build a search. Only an explicitly entered query leaves the app. */
object GifPolicy {
    fun searchUrl(query: String): String = "https://www.google.com/search?udm=2&tbs=itp%3Aanimated&q=" +
        URLEncoder.encode(query.trim().take(500).ifBlank { "reaction GIF" }, "UTF-8")

    fun httpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    fun acceptsGif(types: Array<String>): Boolean = types.any {
        it.equals("image/gif", true) || it.equals("image/*", true) || it == "*/*"
    }

    fun canAttach(expectedEditor: Any, currentEditor: Any?, privateField: Boolean, types: Array<String>): Boolean =
        expectedEditor === currentEditor && !privateField && acceptsGif(types)

    fun fileId(id: String): Boolean = Regex("[a-f0-9]{64}").matches(id)

    fun recentIds(existing: List<String>, id: String): List<String> =
        (listOf(id) + existing).filter(::fileId).distinct().take(KineticaConstants.GIF_RECENTS)

    /** Validate block boundaries, dimensions and at least two frames; never send an HTML page
     * or a static search thumbnail disguised with a .gif extension. Decoding is done by Android. */
    fun animatedGif(bytes: ByteArray): Boolean = runCatching {
        require(bytes.size in 14..KineticaConstants.GIF_MAX_BYTES)
        require(String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a"))
        fun u(i: Int) = bytes[i].toInt() and 255
        fun word(i: Int) = u(i) or (u(i + 1) shl 8)
        require(word(6) in 1..KineticaConstants.GIF_MAX_DIMENSION)
        require(word(8) in 1..KineticaConstants.GIF_MAX_DIMENSION)
        var p = 13
        val packed = u(10)
        if (packed and 128 != 0) p += 3 * (1 shl ((packed and 7) + 1))
        var frames = 0
        fun blocks() {
            while (true) {
                val size = u(p++)
                if (size == 0) return
                p += size
                require(p <= bytes.size)
            }
        }
        while (p < bytes.size) {
            when (u(p++)) {
                0x3b -> return@runCatching frames >= 2
                0x21 -> { p++; blocks() }
                0x2c -> {
                    require(word(p + 4) in 1..KineticaConstants.GIF_MAX_DIMENSION)
                    require(word(p + 6) in 1..KineticaConstants.GIF_MAX_DIMENSION)
                    val flags = u(p + 8)
                    p += 9
                    if (flags and 128 != 0) p += 3 * (1 shl ((flags and 7) + 1))
                    require(u(p++) in 2..8)
                    blocks()
                    frames++
                    require(frames <= KineticaConstants.GIF_MAX_FRAMES)
                }
                else -> return@runCatching false
            }
        }
        false
    }.getOrDefault(false)
}
