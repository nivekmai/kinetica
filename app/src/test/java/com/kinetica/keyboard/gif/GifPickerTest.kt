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

    @Test fun keyboardHeightSearchSheetClampsOnRotationAndSmallWindows() {
        assertEquals(360, GifSheetSizing.height(360, 900))
        assertEquals(180, GifSheetSizing.height(360, 180))
        assertEquals(1, GifSheetSizing.height(360, 0))
        assertEquals(1, GifSheetSizing.height(-1, 100))
        assertEquals(1, GifSheetSizing.height(0, -40))
    }
}
