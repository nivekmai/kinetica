package com.kinetica.keyboard.gif

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GifTransferTest {
    private val selected = GifTransfer.Pending("a".repeat(64), "messenger", 42)

    @Before fun reset() { GifTransfer.pending = null }
    @After fun clear() { GifTransfer.pending = null }

    @Test fun reopeningEditorAfterAbcDoesNotKeepOfferingAnUnsentGif() {
        GifTransfer.pending = selected
        assertEquals(selected, GifTransfer.takeReturnOffer("messenger", 42))
        // Selecting ABC leaves the file available, but a restart/hide/show must
        // not interpret the unsent file as another request to open the panel.
        assertEquals(selected, GifTransfer.pending)
        repeat(3) { assertNull(GifTransfer.takeReturnOffer("messenger", 42)) }
    }

    @Test fun gifCapableFieldWithoutAnExplicitSelectionHasNoReturnOffer() {
        assertTrue(GifPolicy.acceptsGif(arrayOf("image/gif")))
        assertNull(GifTransfer.takeReturnOffer("messenger", 42))
    }

    @Test fun browserQueryAndOtherEditorsDoNotStealTheReturnOffer() {
        GifTransfer.pending = selected
        assertNull(GifTransfer.takeReturnOffer("keyboard.search", 42))
        assertNull(GifTransfer.takeReturnOffer("messenger", 43))
        assertNull(GifTransfer.takeReturnOffer(null, 42))
        assertEquals(selected, GifTransfer.takeReturnOffer("messenger", 42))
    }

    @Test fun anotherExplicitSelectionCanOfferTheSameGifAgainOnce() {
        GifTransfer.pending = selected
        assertEquals(selected, GifTransfer.takeReturnOffer("messenger", 42))
        GifTransfer.pending = selected
        assertEquals(selected, GifTransfer.takeReturnOffer("messenger", 42))
        assertNull(GifTransfer.takeReturnOffer("messenger", 42))
    }

    @Test fun manualPickerEntryAcknowledgesTheOfferWithoutLosingTheFile() {
        GifTransfer.pending = selected
        GifTransfer.acknowledgeReturnOffer()
        assertNull(GifTransfer.takeReturnOffer("messenger", 42))
        assertEquals(selected, GifTransfer.pending)
    }

    @Test fun attachingOrRemovingTheSelectionCancelsItsReturnOffer() {
        GifTransfer.pending = selected
        GifTransfer.pending = null
        assertNull(GifTransfer.takeReturnOffer("messenger", 42))
        assertNull(GifTransfer.pending)
    }
}
