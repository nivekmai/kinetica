package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceRecognitionRunTest {
    @Test fun silenceBetweenSegmentsDoesNotEndTheRequestOrEraseLaterWords() {
        val request = VoiceRecognitionRun(streaming = true)
        val draft = VoiceDraft()
        draft.partial("first thought")
        draft.finish(request.segment("First thought.")!!)
        repeat(3) { assertFalse(request.waitForFinalOnSilence) }
        draft.partial("after a long pause")
        draft.finish(request.segment("After a long pause.")!!)
        assertEquals("First thought. After a long pause.", draft.text)
        assertFalse(request.waitForFinalOnSilence)
    }
    @Test fun fullSessionFinalAfterSegmentsDoesNotDuplicateAlreadyInsertedText() {
        val request = VoiceRecognitionRun(true)
        val draft = VoiceDraft()
        draft.finish(request.segment("One.")!!)
        draft.finish(request.segment("Two.")!!)
        draft.finish(request.complete("One. Two.")!!)
        assertEquals("One. Two.", draft.text)
        assertNull(request.complete("One. Two."))
        assertNull(request.segment("stale callback"))
    }
    @Test fun closingAudioKeepsTheLastPartialIfNoFinalSegmentArrives() {
        val request = VoiceRecognitionRun(true)
        val draft = VoiceDraft()
        draft.finish(request.segment("First.")!!)
        draft.partial("last words")
        draft.finish(request.complete("")!!)
        assertEquals("First. last words", draft.text)
    }
    @Test fun legacyProviderStillUsesFinalResultsAndEndOfSpeechTimeout() {
        val request = VoiceRecognitionRun(false)
        assertTrue(request.waitForFinalOnSilence)
        assertEquals("recognized words", request.complete("recognized words"))
        assertFalse(request.waitForFinalOnSilence)
        assertNull(request.complete("late duplicate"))
    }
}
