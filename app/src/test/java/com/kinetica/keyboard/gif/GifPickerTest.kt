package com.kinetica.keyboard.gif

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GifPickerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun removingOneRecentPersistsWithoutDeletingOthersOrGrantedMedia() {
        val cache = temporary.newFolder("media")
        val index = File(temporary.root, "recents.txt")
        val ids = (1..3).map { "%064x".format(it) }
        ids.forEach { File(cache, "$it.gif").writeText("media still readable by recipient") }
        val store = GifStore(cache, index)
        ids.forEach(store::remember)
        store.remove(ids[1])
        // Read a new instance so the test checks disk persistence, not an in-memory list.
        assertEquals(listOf(ids[2], ids[0]), GifStore(cache, index).recents())
        ids.forEach { assertNotNull(store.file(it)) }
        store.remove(ids[1])
        assertEquals(listOf(ids[2], ids[0]), store.recents())
        store.remove(ids[2]); store.remove(ids[0])
        assertTrue(GifStore(cache, index).recents().isEmpty())
    }

    @Test fun removingPendingOnlyGifLeavesRecentHistoryUntouched() {
        val cache = temporary.newFolder("media")
        val store = GifStore(cache, File(temporary.root, "recents.txt"))
        val recent = "a".repeat(64)
        val pending = "b".repeat(64)
        File(cache, "$recent.gif").writeText("recent")
        File(cache, "$pending.gif").writeText("pending")
        store.remember(recent)
        store.remove(pending)
        assertEquals(listOf(recent), store.recents())
        assertNotNull(store.file(pending))
    }

    @Test fun staggeredCardsUseGifDimensionsBeforeBitmapDecoding() {
        val landscape = byteArrayOf(71, 73, 70, 56, 57, 97, -112, 1, -56, 0)
        assertEquals(400 to 200, GifTileSizing.dimensions(landscape))
        assertNull(GifTileSizing.dimensions(landscape.copyOf(9)))
        assertNull(GifTileSizing.dimensions("not a GIF!".toByteArray()))
        assertNull(GifTileSizing.dimensions(landscape.clone().apply { this[6] = 0; this[7] = 0 }))
        assertEquals(60, GifTileSizing.height(120, 400, 200, 48))
        assertEquals(120, GifTileSizing.height(120, 200, 200, 48))
        assertEquals(240, GifTileSizing.height(120, 200, 400, 48))
    }

    @Test fun extremeGifShapesStayTappableAndDoNotFillTheWholeScrollRange() {
        assertEquals(48, GifTileSizing.height(120, 2048, 1, 48))
        assertEquals(360, GifTileSizing.height(120, 1, 2048, 48))
        assertEquals(120, GifTileSizing.height(120, 0, 0, 48))
        assertTrue(GifTileSizing.height(Int.MAX_VALUE, 1, 2048, 48) > 0)
    }

    @Test fun searchSheetUsesOneAndAHalfKeyboardHeightsAndClampsToAvailableSpace() {
        assertEquals(540, GifSheetSizing.height(360, 900))
        assertEquals(540, GifSheetSizing.height(360, 600))
        assertEquals(180, GifSheetSizing.height(360, 180))
        assertEquals(Int.MAX_VALUE, GifSheetSizing.height(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(1, GifSheetSizing.height(360, 0))
        assertEquals(1, GifSheetSizing.height(-1, 100))
        assertEquals(1, GifSheetSizing.height(0, -40))
    }
}
