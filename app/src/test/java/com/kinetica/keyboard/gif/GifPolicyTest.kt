package com.kinetica.keyboard.gif

import com.kinetica.keyboard.engine.KineticaConstants
import com.kinetica.keyboard.keys.ActionRow
import com.kinetica.keyboard.keys.EditorAction
import com.kinetica.keyboard.settings.KeyboardConfig
import com.kinetica.keyboard.settings.ShortcutKind
import com.kinetica.keyboard.settings.ShortcutKinds
import org.junit.Assert.*
import org.junit.Test

class GifPolicyTest {
    // Valid 1x1 GIF frames, including extensions and a global color table.
    private fun gif(frames: Int): ByteArray {
        val header = byteArrayOf(71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0, 0, 0, 0, -1, -1, -1)
        val frame = byteArrayOf(0x21, -7, 4, 0, 10, 0, 0, 0, 0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0)
        return header + (0 until frames).fold(byteArrayOf()) { b, _ -> b + frame } + byteArrayOf(0x3b)
    }
    @Test fun animationRequiredRatherThanThumbnailOrPage() {
        assertTrue(GifPolicy.animatedGif(gif(2)))
        assertFalse(GifPolicy.animatedGif(gif(1)))
        assertFalse(GifPolicy.animatedGif("<html>https://example.com/cat.gif</html>".toByteArray()))
        assertFalse(GifPolicy.animatedGif("RIFF....WEBP".toByteArray()))
    }
    @Test fun truncatedOrHugeGifDoesNotPass() {
        val valid = gif(2)
        for (length in valid.indices) assertFalse("prefix $length", GifPolicy.animatedGif(valid.copyOf(length)))
        val huge = valid.clone().apply { this[6] = -1; this[7] = 127 }
        assertFalse(GifPolicy.animatedGif(huge))
        assertFalse(GifPolicy.animatedGif(valid.copyOf(KineticaConstants.GIF_MAX_BYTES + 1)))
        assertFalse(GifPolicy.animatedGif(gif(KineticaConstants.GIF_MAX_FRAMES + 1)))
    }
    @Test fun downloadTargetsMustBeSecureAndNotAppCommands() {
        assertTrue(GifPolicy.httpsUrl("https://media.example.com/a.gif?x=1"))
        for (bad in listOf("http://example.com/a.gif", "file:///data/a.gif", "javascript:alert(1)",
            "data:image/gif;base64,xxx", "intent://example.com", "https://user:pass@example.com/a.gif", "https://example.com:123/a.gif")) {
            assertFalse(bad, GifPolicy.httpsUrl(bad))
        }
    }
    @Test fun queryIsEncodedAndNotAnExecutableUrl() {
        val url = GifPolicy.searchUrl("cats & dogs #hi")
        assertEquals("https://www.google.com/search?udm=2&tbs=itp%3Aanimated&q=cats+%26+dogs+%23hi", url)
        assertTrue(GifPolicy.searchUrl(" ").endsWith("reaction+GIF"))
    }
    @Test fun recentHistoryIsUniqueNewestFirstAndBounded() {
        val ids = (1..40).map { "%064x".format(it) }
        val result = GifPolicy.recentIds(ids, ids[10])
        assertEquals(ids[10], result.first())
        assertEquals(30, result.size)
        assertEquals(30, result.distinct().size)
        assertEquals(listOf(ids[0]), GifPolicy.recentIds(listOf("../private.txt", "not-a-hash"), ids[0]))
    }
    @Test fun advertisedMimeTypesGateInsertion() {
        assertTrue(GifPolicy.acceptsGif(arrayOf("image/*")))
        assertTrue(GifPolicy.acceptsGif(arrayOf("image/png", "image/gif")))
        assertTrue(GifPolicy.acceptsGif(arrayOf("*/*")))
        assertFalse(GifPolicy.acceptsGif(emptyArray()))
        assertFalse(GifPolicy.acceptsGif(arrayOf("image/png", "text/plain")))
    }
    @Test fun staleEditorTapCannotAttachIntoAnotherConversation() {
        val original = Any()
        assertTrue(GifPolicy.canAttach(original, original, false, arrayOf("image/gif")))
        assertFalse(GifPolicy.canAttach(original, Any(), false, arrayOf("image/gif")))
        assertFalse(GifPolicy.canAttach(original, null, false, arrayOf("image/gif")))
        assertFalse(GifPolicy.canAttach(original, original, true, arrayOf("image/gif")))
        assertFalse(GifPolicy.canAttach(original, original, false, arrayOf("text/plain")))
    }
    @Test fun gifsShortcutWorksOnEnterAndEveryPicker() {
        val alternate = KeyboardConfig.parseEnterAlternates(":gifs ?")[0]
        assertEquals(EditorAction.GIFS, ActionRow.actionForAlternate(alternate, false))
        assertEquals("GIF", ActionRow.alternateLabel(alternate))
        val kind = ShortcutKind.Action(EditorAction.GIFS)
        for (picker in listOf(ShortcutKinds.chords, ShortcutKinds.edgeSwipes, ShortcutKinds.expansions)) assertTrue(picker.contains(kind))
        assertTrue(ActionRow.ALL.contains(EditorAction.GIFS))
        assertEquals(kind, ShortcutKinds.decode(ShortcutKinds.encode(kind, "")).first)
    }
}
