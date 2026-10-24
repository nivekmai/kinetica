package com.kinetica.keyboard.gif

import org.junit.Assert.*
import org.junit.Test

class GifPreviewSessionTest {
    private class Animation {
        var running = false
        var starts = 0
        fun start() { running = true; starts++ }
        fun stop() { running = false }
    }
    private var shown: Animation? = null
    private val preview = GifPreviewSession<Animation>(
        show = { shown = it }, start = { it.start() }, stop = { it.stop() },
    )

    @Test fun decodedConfirmationPreviewStartsPlayingWithoutAnotherTap() {
        val ticket = preview.attach(true)
        val animation = Animation()
        assertTrue(preview.decoded(ticket, animation))
        assertSame(animation, shown)
        assertTrue(animation.running)
        assertEquals(1, animation.starts)
    }

    @Test fun hiddenPreviewWaitsThenResumesOnlyWhenVisible() {
        val ticket = preview.attach(false)
        val animation = Animation()
        preview.decoded(ticket, animation)
        assertFalse(animation.running)
        preview.visibility(true)
        assertTrue(animation.running)
        preview.visibility(true)
        assertEquals(1, animation.starts)
        preview.visibility(false)
        assertFalse(animation.running)
        preview.visibility(true)
        assertTrue(animation.running)
    }

    @Test fun closingDialogStopsPlaybackAndClearsItsDrawable() {
        val ticket = preview.attach(true)
        val animation = Animation()
        preview.decoded(ticket, animation)
        preview.detach()
        assertFalse(animation.running)
        assertNull(shown)
        preview.visibility(true)
        assertFalse(animation.running)
    }

    @Test fun decodeFinishingAfterCancelCannotStartOrShowAStaleGif() {
        val ticket = preview.attach(true)
        preview.detach()
        val animation = Animation()
        assertFalse(preview.decoded(ticket, animation))
        assertNull(shown)
        assertEquals(0, animation.starts)
    }

    @Test fun lateDecodeFromOldAttachmentCannotReplaceNewPreview() {
        val oldTicket = preview.attach(true)
        preview.detach()
        val newTicket = preview.attach(true)
        val current = Animation()
        preview.decoded(newTicket, current)
        val stale = Animation()
        assertFalse(preview.decoded(oldTicket, stale))
        assertSame(current, shown)
        assertTrue(current.running)
        assertEquals(0, stale.starts)
    }

    @Test fun decodeFailureLeavesNoOldAnimationRunning() {
        val ticket = preview.attach(true)
        val animation = Animation()
        preview.decoded(ticket, animation)
        assertTrue(preview.decoded(ticket, null))
        assertFalse(animation.running)
        assertNull(shown)
    }
}
