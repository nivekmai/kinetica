package com.kinetica.keyboard.ime

import com.kinetica.keyboard.engine.KineticaConstants

/** Counts and monotonic touch durations only; never stores the text being typed. */
class TypingHistory {
    data class Sample(val chars: Long, val activeMs: Long, val peakWpm: Double = 0.0)
    private var lastEnd: Long? = null
    private var pendingMs = 0L
    private var pendingPaused = false
    private val window = ArrayDeque<Sample>()

    fun touch(start: Long, end: Long) {
        if (start < 0 || end < start) return
        val previous = lastEnd
        val pause = previous == null || start - previous > TypingSpeed.IDLE_BREAK_MS
        if (pause) {
            window.clear()
            if (pendingMs > 0) pendingPaused = true
        }
        val from = if (pause) start else previous!!
        pendingMs += (end - from).coerceAtLeast(0)
        lastEnd = maxOf(end, previous ?: end)
    }

    fun commit(chars: Int): Sample? {
        val elapsed = pendingMs
        val paused = pendingPaused
        pendingMs = 0
        pendingPaused = false
        if (chars <= 0 || elapsed <= 0) return null
        val sample = Sample(chars.toLong(), elapsed)
        // The final word length cannot tell us which letters came before a pause.
        // Keep its volume/time for the average, but never claim a sustained peak.
        if (paused) return sample
        window.addLast(sample)
        while (window.size > 1 && window.sumOf { it.activeMs } - window.first().activeMs >= TypingSpeed.SPAN_MS) {
            window.removeFirst()
        }
        val ms = window.sumOf { it.activeMs }
        val count = window.sumOf { it.chars }
        val peak = if (ms >= KineticaConstants.TYPING_STATS_PEAK_MIN_MS &&
            count >= KineticaConstants.TYPING_STATS_PEAK_MIN_CHARS) wpm(count, ms) else 0.0
        return sample.copy(peakWpm = peak)
    }

    fun discardWord() { pendingMs = 0; pendingPaused = false }
    fun reset() { lastEnd = null; discardWord(); window.clear() }

    data class Bucket(val day: String, val hour: Int, val chars: Long, val activeMs: Long, val peakWpm: Double)
    data class Summary(val chars: Long, val activeMs: Long, val peakWpm: Double) {
        val averageWpm: Double get() = wpm(chars, activeMs)
    }
    companion object {
        fun wpm(chars: Long, ms: Long) = if (ms > 0) chars * 12000.0 / ms else 0.0
        fun summarize(buckets: List<Bucket>) = Summary(buckets.sumOf { it.chars }, buckets.sumOf { it.activeMs },
            buckets.maxOfOrNull { it.peakWpm } ?: 0.0)
    }
}
