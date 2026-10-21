package com.kinetica.keyboard.keys

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class NavigationLayerTest {
    private val steps = mutableListOf<Int>()
    private val controller = SpacebarCursorController(1f) { dir, _ -> steps.add(dir) }
    private fun down() = controller.onDown(100f, 0f, 200f, 100f, 32f)

    @Test fun upwardSwipeWithHorizontalDriftOpensNavigationWithoutMovingOrSpacing() {
        down()
        controller.onMove(103f, 88f)
        controller.onMove(113f, 60f)
        assertEquals(SpacebarCursorController.Lift.NAVIGATION, controller.onUp())
        assertTrue(steps.isEmpty())
    }

    @Test fun horizontalSlideCannotTurnIntoNavigation() {
        down()
        controller.onMove(108f, 98f)
        controller.onMove(148f, 50f)
        assertEquals(SpacebarCursorController.Lift.SLIDE, controller.onUp())
        assertEquals(listOf(1, 1), steps)
    }

    @Test fun shortOrRetractedVerticalSwipeDoesNotOpenNavigationOrTypeSpace() {
        down()
        controller.onMove(100f, 88f)
        assertEquals(SpacebarCursorController.Lift.SLIDE, controller.onUp())
        down()
        controller.onMove(100f, 50f)
        controller.onMove(100f, 95f)
        assertEquals(SpacebarCursorController.Lift.SLIDE, controller.onUp())
    }

    @Test fun consumedChordAndNextTapDoNotCarryNavigationOrDoubleSpace() {
        controller.doubleSpacePeriod = true
        down()
        controller.onUp(100)
        down()
        controller.onMove(100f, 50f)
        controller.consume()
        down()
        assertEquals(SpacebarCursorController.Lift.SPACE, controller.onUp(150))
    }

    @Test fun navigationWinsOverSpacelessAndDoubleTapModes() {
        controller.spacelessZone = true
        controller.doubleSpacePeriod = true
        controller.onDown(10f, 0f, 200f, 100f, 32f)
        controller.onMove(12f, 50f)
        assertEquals(SpacebarCursorController.Lift.NAVIGATION, controller.onUp(200))
    }

    @Test fun movementAndSelectionModifiersAreIndependentAndLineJumpsAreNotDocumentJumps() {
        val expected = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END)
        for ((i, move) in NavigationMove.entries.withIndex()) {
            val plain = move.combo(false)
            val selection = move.combo(true)
            assertEquals(expected[i], plain.keyCode())
            assertEquals(0, plain.metaState() and KeyEvent.META_SHIFT_ON)
            assertTrue(selection.metaState() and KeyEvent.META_SHIFT_ON != 0)
            assertEquals(move == NavigationMove.WORD_LEFT || move == NavigationMove.WORD_RIGHT, plain.ctrl)
            assertEquals(plain.copy(shift = true), selection)
            assertFalse(move.combo(false).shift)
        }
    }
}
