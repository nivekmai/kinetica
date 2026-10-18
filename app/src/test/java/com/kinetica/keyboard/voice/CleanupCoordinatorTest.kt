package com.kinetica.keyboard.voice

import com.kinetica.keyboard.engine.KineticaConstants as K
import org.junit.Assert.*
import org.junit.Test

class CleanupCoordinatorTest {
    @Test fun recognitionRevisesLiveWordsWhileEarlierJobRunsAndNewSpeechIsCoalesced() {
        val c = CleanupCoordinator(); val d = VoiceDraft()
        d.finish("I cut home"); c.finalized(0)
        assertNull(c.start(0))
        val first = c.start(K.VOICE_CLEANUP_DELAY_MS)!!; val snapshot = d.snapshot()!!
        d.partial("And"); d.partial("and then")
        assertEquals("I cut home and then", d.text)
        assertEquals(first, c.active)
        d.finish("and then I made dinner"); c.finalized(2000)
        d.finish("For my family"); c.finalized(2100)
        assertNull(c.start(10000))
        assertTrue(d.apply(snapshot, "I got home."))
        assertTrue(c.finish(first, true, false, 10000))
        assertTrue(c.pending) // Show waiting, not a misleading all-updated status.
        assertEquals("I got home. and then I made dinner For my family", d.text)
        assertEquals(0L, c.delay(10000))
        val next = c.start(10000)!!
        assertEquals(3L, next.revision) // No intermediate queued job for revision 2.
        assertEquals("I cut home and then I made dinner For my family", d.snapshot()!!.original)
        assertEquals(1L, c.appliedRevision)
    }
    @Test fun fastJobsRemainThrottledAndRecentSpeechStillGetsDebounced() {
        val c = CleanupCoordinator(); c.finalized(0)
        val first = c.start(K.VOICE_CLEANUP_DELAY_MS)!!
        c.finalized(first.started + 100)
        c.finish(first, true, false, first.started + 200)
        assertEquals(K.VOICE_CLEANUP_INTERVAL_MS - 200, c.delay(first.started + 200))
        val second = c.start(first.started + K.VOICE_CLEANUP_INTERVAL_MS)!!
        val end = second.started + 9000
        c.finalized(end - 100)
        c.finish(second, true, false, end)
        assertEquals(K.VOICE_CLEANUP_DELAY_MS - 100, c.delay(end))
        assertNull(c.start(end))
        assertNotNull(c.start(end + K.VOICE_CLEANUP_DELAY_MS - 100))
    }
    @Test fun failureDoesNotLoopAndRetryUsesLatestRevision() {
        val c = CleanupCoordinator(); c.finalized(0)
        val first = c.start(K.VOICE_CLEANUP_DELAY_MS)!!
        c.finalized(2000)
        assertTrue(c.finish(first, false, true, 40000))
        assertNull(c.delay(100000))
        c.retry(100000)
        assertEquals(2L, c.start(100000)!!.revision)
    }
    @Test fun timeoutAndLateCompletionCannotFinishANewerJob() {
        val c = CleanupCoordinator(); c.finalized(0)
        val first = c.start(K.VOICE_CLEANUP_DELAY_MS)!!
        assertTrue(c.finish(first, false, true, 40000))
        c.retry(41000); val next = c.start(41000)!!
        assertFalse(c.finish(first, true, false, 42000))
        assertEquals(next, c.active)
        assertEquals(0L, c.appliedRevision)
    }
    @Test fun disablingCancelsAndReenablingExplicitlyRetries() {
        val c = CleanupCoordinator(); c.finalized(0)
        val old = c.start(K.VOICE_CLEANUP_DELAY_MS)!!
        c.setEnabled(false, 2000)
        assertNull(c.active); assertNull(c.delay(5000))
        assertFalse(c.finish(old, true, false, 5000))
        c.setEnabled(true, 6000)
        assertNotNull(c.start(6000))
        c.cancel(); c.finalized(7000)
        assertNull(c.start(100000))
    }
    @Test fun memoryPressureStopsQueuedAndRunningWorkWithoutLeavingAnEndlessWaitingState() {
        val c = CleanupCoordinator(); c.finalized(0)
        c.suspendWork()
        assertFalse(c.pending); assertNull(c.delay(10000))
        c.retry(10000); val old = c.start(10000)!!
        c.finalized(11000); c.suspendWork()
        assertNull(c.active); assertFalse(c.pending)
        assertFalse(c.finish(old, true, false, 12000))
        c.retry(13000); assertEquals(2L, c.start(13000)!!.revision)
    }
    @Test fun unchangedResultFinishesWithoutInventingAnotherRevision() {
        val c = CleanupCoordinator(); c.finalized(0)
        val first = c.start(K.VOICE_CLEANUP_DELAY_MS)!!
        c.finish(first, true, false, 3000)
        assertNull(c.delay(100000)); assertEquals(1L, c.appliedRevision)
    }
}
