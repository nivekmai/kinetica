package com.kinetica.keyboard.keys

import android.view.KeyEvent

/**
 * ArrowKeyMovementMethod reads Shift state stored in the editor's text buffer,
 * not just the arrow event's meta bits. Bracket each selected move with real
 * Shift events. Keep this sequence synchronous on one captured InputConnection.
 */
object NavigationKeys {
    data class Stroke(val action: Int, val keyCode: Int, val metaState: Int)

    fun send(combo: KeyCombo, emit: (Stroke) -> Boolean, clearShift: () -> Unit): Boolean {
        val meta = combo.metaState()
        var delivered = true
        try {
            if (combo.shift) delivered = emit(Stroke(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, meta))
            if (delivered) {
                try {
                    delivered = emit(Stroke(KeyEvent.ACTION_DOWN, combo.keyCode(), meta))
                } finally {
                    val released = emit(Stroke(KeyEvent.ACTION_UP, combo.keyCode(), meta))
                    delivered = delivered && released
                }
            }
        } finally {
            if (combo.shift) {
                try {
                    val released = emit(Stroke(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT,
                        meta and (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON).inv()))
                    delivered = delivered && released
                } finally {
                    // An unhandled move at a boundary can otherwise leave a latched Shift span.
                    clearShift()
                }
            }
        }
        return delivered
    }
}
