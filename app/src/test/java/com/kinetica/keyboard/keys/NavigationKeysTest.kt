package com.kinetica.keyboard.keys

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class NavigationKeysTest {
    /** Models the relevant Android contract: selection depends on Shift-down state, not meta bits. */
    private class Editor {
        var anchor = 5
        var caret = 5
        var shift = false
        val strokes = mutableListOf<NavigationKeys.Stroke>()
        var cleared = 0
        fun emit(s: NavigationKeys.Stroke): Boolean {
            strokes.add(s)
            if (s.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT) shift = s.action == KeyEvent.ACTION_DOWN
            else if (s.action == KeyEvent.ACTION_DOWN) {
                val delta = if (s.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                caret = (caret + delta).coerceIn(0, 10)
                if (!shift) anchor = caret
            }
            return true
        }
        fun move(move: NavigationMove, selecting: Boolean) = NavigationKeys.send(move.combo(selecting), ::emit) {
            shift = false; cleared++
        }
    }

    @Test fun selectedArrowsExtendAndReverseAcrossTheAnchorInsteadOfMovingWithoutHighlight() {
        val e = Editor()
        repeat(3) { e.move(NavigationMove.RIGHT, true) }
        assertEquals(5, e.anchor)
        assertEquals(8, e.caret)
        repeat(4) { e.move(NavigationMove.LEFT, true) }
        assertEquals(5, e.anchor)
        assertEquals(4, e.caret)
        assertFalse(e.shift)
        assertEquals(7, e.cleared)
    }

    @Test fun modifierFlagsAloneReproduceTheOldNoSelectionFailure() {
        val e = Editor()
        val combo = NavigationMove.RIGHT.combo(true)
        e.emit(NavigationKeys.Stroke(KeyEvent.ACTION_DOWN, combo.keyCode(), combo.metaState()))
        e.emit(NavigationKeys.Stroke(KeyEvent.ACTION_UP, combo.keyCode(), combo.metaState()))
        assertEquals(e.anchor, e.caret)
        e.move(NavigationMove.RIGHT, true)
        assertNotEquals(e.anchor, e.caret)
    }

    @Test fun everyMovementBracketsShiftAndRetainsWordModifiers() {
        for (move in NavigationMove.entries) {
            val e = Editor()
            assertTrue(e.move(move, true))
            assertEquals(listOf(KeyEvent.KEYCODE_SHIFT_LEFT, move.combo(true).keyCode(),
                move.combo(true).keyCode(), KeyEvent.KEYCODE_SHIFT_LEFT), e.strokes.map { it.keyCode })
            assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP, KeyEvent.ACTION_UP),
                e.strokes.map { it.action })
            assertEquals(move.combo(true).metaState(), e.strokes[1].metaState)
            assertEquals(0, e.strokes.last().metaState and KeyEvent.META_SHIFT_ON)
            assertFalse(e.shift)
        }
    }

    @Test fun turningSelectOffSendsNoShiftAndFollowingMovementDoesNotExtend() {
        val e = Editor()
        e.move(NavigationMove.LEFT, true)
        e.strokes.clear()
        e.move(NavigationMove.RIGHT, false)
        assertEquals(e.anchor, e.caret)
        assertEquals(2, e.strokes.size)
        assertTrue(e.strokes.all { it.metaState and KeyEvent.META_SHIFT_ON == 0 })
        assertEquals(1, e.cleared)
    }

    @Test fun refusedMovementStillReleasesAndClearsShift() {
        val e = Editor()
        val success = NavigationKeys.send(NavigationMove.LEFT.combo(true), {
            e.emit(it)
            it.keyCode != KeyEvent.KEYCODE_DPAD_LEFT
        }) { e.shift = false; e.cleared++ }
        assertFalse(success)
        assertFalse(e.shift)
        assertEquals(1, e.cleared)
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, e.strokes.last().keyCode)
    }

    @Test fun connectionExceptionStillAttemptsBothReleasesAndClearsShift() {
        val e = Editor()
        try {
            NavigationKeys.send(NavigationMove.LEFT.combo(true), {
                e.emit(it)
                if (it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && it.action == KeyEvent.ACTION_DOWN)
                    throw IllegalStateException("Disconnected")
                true
            }) { e.shift = false; e.cleared++ }
            fail("Expected disconnect")
        } catch (_: IllegalStateException) {
            assertEquals(4, e.strokes.size)
            assertFalse(e.shift)
            assertEquals(1, e.cleared)
        }
    }

    @Test fun refusedShiftPressDoesNotSendAnUnselectedMove() {
        val e = Editor()
        assertFalse(NavigationKeys.send(NavigationMove.RIGHT.combo(true), {
            e.emit(it); false
        }) { e.shift = false; e.cleared++ })
        assertEquals(5, e.caret)
        assertEquals(2, e.strokes.size)
        assertFalse(e.shift)
        assertEquals(1, e.cleared)
    }
}
