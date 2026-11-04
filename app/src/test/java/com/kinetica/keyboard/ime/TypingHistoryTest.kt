package com.kinetica.keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class TypingHistoryTest {
    @Test fun averageUsesTotalActiveTimeRatherThanAveragingWordSpeeds() {
        val rows = listOf(TypingHistory.Bucket("2026-11-04", 8, 50, 10000, 60.0),
            TypingHistory.Bucket("2026-11-04", 9, 50, 30000, 20.0))
        val total = TypingHistory.summarize(rows)
        assertEquals(30.0, total.averageWpm, 0.001) // Not the unweighted 40 WPM.
        assertEquals(60.0, total.peakWpm, 0.001)
        assertEquals(40000, total.activeMs)
    }

    @Test fun shortInterWordGapsCountButReadingPausesDoNot() {
        val meter = TypingHistory()
        meter.touch(0, 600)
        assertEquals(600, meter.commit(5)!!.activeMs)
        meter.touch(1200, 1800)
        assertEquals(1200, meter.commit(5)!!.activeMs)
        meter.touch(20000, 20600)
        assertEquals(600, meter.commit(5)!!.activeMs)
    }

    @Test fun pauseInsideAWordIsExcludedWithoutLosingItsActualTouchTime() {
        val meter = TypingHistory()
        meter.touch(0, 100)
        meter.touch(8000, 8100)
        meter.touch(8200, 8300)
        assertEquals(400, meter.commit(4)!!.activeMs)
    }

    @Test fun overlappingThumbsDoNotDoubleCountElapsedTime() {
        val meter = TypingHistory()
        meter.touch(0, 200)
        meter.touch(100, 300)
        meter.touch(120, 180)
        assertEquals(300, meter.commit(4)!!.activeMs)
    }

    @Test fun aWordAcrossALongPauseContributesToAverageButNotASustainedPeak() {
        val meter = TypingHistory()
        meter.touch(0, 6000)
        meter.touch(20000, 26000)
        val sample = meter.commit(60)!!
        assertEquals(12000, sample.activeMs)
        assertEquals(0.0, sample.peakWpm, 0.0)
    }

    @Test fun hiddenOrChangedInputDoesNotBridgeTimeIntoTheNextSession() {
        val meter = TypingHistory()
        meter.touch(0, 100)
        meter.commit(5)
        meter.reset()
        meter.touch(2000, 2100)
        assertEquals(100, meter.commit(5)!!.activeMs)
    }

    @Test fun pastedOrPredictedTextWithoutTouchTimeCannotCreateASample() {
        val meter = TypingHistory()
        assertNull(meter.commit(1000))
        meter.touch(0, 100)
        meter.discardWord()
        assertNull(meter.commit(1000))
        meter.touch(200, 300)
        assertNotNull(meter.commit(1))
        assertNull(meter.commit(1000)) // Never count the same touches twice.
    }

    @Test fun peakRequiresSustainedTypingAndResetsAfterAPause() {
        val meter = TypingHistory()
        repeat(9) { i ->
            meter.touch(i * 1000L, (i + 1) * 1000L)
            assertEquals(0.0, meter.commit(5)!!.peakWpm, 0.0)
        }
        meter.touch(9000, 10000)
        assertEquals(60.0, meter.commit(5)!!.peakWpm, 0.001)
        meter.touch(20000, 20500)
        assertEquals(0.0, meter.commit(50)!!.peakWpm, 0.0)
    }

    @Test fun peakWindowEventuallyReflectsANewPace() {
        val meter = TypingHistory()
        repeat(20) { i -> meter.touch(i * 1000L, (i + 1) * 1000L); meter.commit(5) }
        var last: TypingHistory.Sample? = null
        repeat(20) { i ->
            meter.touch(20000 + i * 1000L, 21000 + i * 1000L)
            last = meter.commit(10)
        }
        assertEquals(120.0, last!!.peakWpm, 0.001)
    }

    @Test fun zeroLengthAndInvalidTimingCannotCreateInfiniteSpeeds() {
        val meter = TypingHistory()
        meter.touch(-1, 100)
        meter.touch(200, 100)
        meter.touch(300, 300)
        assertNull(meter.commit(5))
        assertEquals(0.0, TypingHistory.summarize(emptyList()).averageWpm, 0.0)
    }
}
