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

    @Before fun reset() { GifTransfer.pending = null; GifTransfer.onAttachmentRequested = null }
    @After fun clear() { GifTransfer.cancelSearch(token); GifTransfer.pending = null; GifTransfer.onAttachmentRequested = null }

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
        assertFalse(GifTransfer.takeAttachment(target, 1001 + KineticaConstants.GIF_ATTACH_READY_TIMEOUT_MS)!!.attach)
        confirm(target.copy(before = null))
        assertNull(GifTransfer.takeAttachment(target.copy(before = null), 1100))
        assertFalse(GifTransfer.takeAttachment(target.copy(before = null), 1001 + KineticaConstants.GIF_ATTACH_READY_TIMEOUT_MS)!!.attach)
    }

    @Test fun unreadableInputDuringReturnDoesNotLoseSelectionBeforeEditorIsReady() {
        confirm()
        assertNull(GifTransfer.takeAttachment(null, 1050))
        assertNull(GifTransfer.takeAttachment(target.copy(before = null, after = null), 1100))
        assertTrue(GifTransfer.hasAttachmentRequest)
        assertTrue(GifTransfer.takeAttachment(target, 1200)!!.attach)
        assertNull(GifTransfer.takeAttachment(target, 1300))
    }

    @Test fun confirmationWakesExistingInputWithoutANewLifecycleCallback() {
        var notified = false
        GifTransfer.onAttachmentRequested = {
            notified = true
            assertTrue(GifTransfer.hasAttachmentRequest)
        }
        confirm()
        assertTrue(notified)
        assertTrue(GifTransfer.takeAttachment(target, 1100)!!.attach)
    }

    @Test fun missingConnectionOrBrowserFocusEventuallyExpiresRatherThanAttachingLater() {
        for (browser in listOf(false, true)) {
            confirm()
            assertNull(GifTransfer.takeAttachment(null, 1100, browser))
            assertFalse(GifTransfer.takeAttachment(null, 1001 + KineticaConstants.GIF_ATTACH_READY_TIMEOUT_MS, browser)!!.attach)
            assertNull(GifTransfer.takeAttachment(target, 4000))
            assertEquals(selected, GifTransfer.pending)
        }
    }

    @Test fun unreadableDifferentDestinationCancelsInsteadOfWaiting() {
        confirm()
        assertFalse(GifTransfer.takeAttachment(target.copy(fieldId = 43, before = null), 1100)!!.attach)
        assertNull(GifTransfer.takeAttachment(target, 1200))
    }

    @Test fun diagnosticsContainDecisionsButNeverOriginOrReturnText() {
        confirm(target.copy(before = "PRIVATE_MESSAGE", after = "PRIVATE_SUFFIX"))
        GifTransfer.takeAttachment(target.copy(before = "OTHER_PRIVATE_MESSAGE"), 1100)
        val trace = GifDebug.snapshot()
        assertTrue(trace.contains("destination_mismatch"))
        assertFalse(trace.contains("PRIVATE_MESSAGE"))
        assertFalse(trace.contains("PRIVATE_SUFFIX"))
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
