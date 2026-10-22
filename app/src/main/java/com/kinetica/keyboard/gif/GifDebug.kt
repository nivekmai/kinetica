package com.kinetica.keyboard.gif

import com.kinetica.keyboard.engine.KineticaConstants

/** Bounded in-memory decisions only. Callers must never pass editor/query text or URLs. */
object GifDebug {
    private val events = ArrayDeque<String>()
    fun record(decision: String) {
        if (events.lastOrNull() == decision) return
        events.addLast(decision)
        while (events.size > KineticaConstants.GIF_DEBUG_EVENTS) events.removeFirst()
    }
    fun snapshot(): String = events.joinToString("\n")
}
