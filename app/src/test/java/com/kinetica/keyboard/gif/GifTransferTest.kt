package com.kinetica.keyboard.gif

import com.kinetica.keyboard.engine.KineticaConstants
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GifTransferTest {
    private val id = "a".repeat(64)
    private val selected = GifTransfer.Pending(id, "messenger", 42)
    private val target = GifTransfer.Target("messenger", 42, "message", 1, 5, 5, "hello", "")
    private var token: String? = null

    @Before fun reset() { GifTransfer.pending = null }
    @After fun clear() { GifTransfer.cancelSearch(token); GifTransfer.pending = null }

    private fun confirm(origin: GifTransfer.Target = target) {
        token = GifTransfer.beginSearch(origin)
        assertTrue(GifTransfer.confirmSearch(token, id, 1000))
    }

    @Test fun useThisGifAttachesOnReturnWithoutOpeningRecentsOrNeedingAnotherTap() {
        confirm()
        val returned = GifTransfer.takeAttachment(target, 1100)!!
        assertTrue(returned.attach)
        assertEquals(selected, returned.selection)
        // An app may restart input while accepting content, or the service may
        // receive both onStartInput and onStartInputView. Only one handoff is allowed.
        repeat(3) { assertNull(GifTransfer.takeAttachment(target, 1101)) }
    }

    @Test fun retainedUnsentGifAndMimeSupportDoNotRequestAnotherAttachment() {
        GifTransfer.pending = selected
        assertTrue(GifPolicy.acceptsGif(arrayOf("image/gif")))
        assertNull(GifTransfer.takeAttachment(target, 1100))
        confirm()
        GifTransfer.takeAttachment(target, 1100) // An app rejects this handoff.
        assertEquals(selected, GifTransfer.pending)
        assertNull(GifTransfer.takeAttachment(target, 1200))
    }

    @Test fun searchFieldFocusWhileClosingDoesNotConsumeTheHandoff() {
        confirm()
        assertNull(GifTransfer.takeAttachment(target.copy(packageName = "keyboard.search"), 1100, isBrowserEditor = true))
        assertTrue(GifTransfer.takeAttachment(target, 1200)!!.attach)
    }

    @Test fun anotherEditorOrChangedConversationNeverReceivesTheAttachment() {
        for (changed in listOf(target.copy(packageName = "other"), target.copy(fieldId = 43),
            target.copy(fieldName = "reply"), target.copy(inputType = 2),
            target.copy(selectionStart = 4), target.copy(selectionEnd = 6),
            target.copy(before = "another conversation"), target.copy(after = "new text"))) {
            confirm()
            assertFalse(GifTransfer.takeAttachment(changed, 1100)!!.attach)
            assertEquals(selected, GifTransfer.pending)
            // It must not unexpectedly attach on a later visit to the old field either.
            assertNull(GifTransfer.takeAttachment(target, 1200))
        }
    }

    @Test fun expiredOrUnreadableDestinationRequiresManualRetry() {
        confirm()
        assertFalse(GifTransfer.takeAttachment(target, 1001 + KineticaConstants.GIF_ATTACH_RETURN_TIMEOUT_MS)!!.attach)
        confirm(target.copy(before = null))
        assertFalse(GifTransfer.takeAttachment(target.copy(before = null), 1100)!!.attach)
    }

    @Test fun anotherExplicitConfirmationCanAttachTheSameGifAgainOnce() {
        confirm()
        assertTrue(GifTransfer.takeAttachment(target, 1100)!!.attach)
        confirm()
        assertTrue(GifTransfer.takeAttachment(target, 1100)!!.attach)
        assertNull(GifTransfer.takeAttachment(target, 1200))
    }

    @Test fun canceledOrSupersededSearchCannotEnqueueAnAttachment() {
        token = GifTransfer.beginSearch(target)
        val first = token
        token = GifTransfer.beginSearch(target.copy(fieldId = 43))
        assertFalse(GifTransfer.confirmSearch(first, id, 1000))
        GifTransfer.cancelSearch(token)
        assertFalse(GifTransfer.confirmSearch(token, id, 1000))
        assertNull(GifTransfer.takeAttachment(target, 1100))
        assertNull(GifTransfer.pending)
    }

    @Test fun duplicateConfirmationAndUnvalidatedFileIdsCannotEnqueue() {
        token = GifTransfer.beginSearch(target)
        assertFalse(GifTransfer.confirmSearch(token, "../not-a-gif", 1000))
        assertTrue(GifTransfer.confirmSearch(token, id, 1000))
        assertFalse(GifTransfer.confirmSearch(token, id, 1001))
        assertTrue(GifTransfer.takeAttachment(target, 1100)!!.attach)
        assertNull(GifTransfer.takeAttachment(target, 1200))
    }

    @Test fun manualEntryCancelsAutomaticAttachmentButRetainsTheFile() {
        confirm()
        GifTransfer.cancelAutomaticAttachment()
        assertNull(GifTransfer.takeAttachment(target, 1100))
        assertEquals(selected, GifTransfer.pending)
    }

    @Test fun attachingOrRemovingSelectionClearsTheAutomaticRequest() {
        confirm()
        GifTransfer.pending = null
        assertNull(GifTransfer.takeAttachment(target, 1100))
        assertNull(GifTransfer.pending)
    }
}
