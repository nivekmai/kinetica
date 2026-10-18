package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test

/** Synthetic equivalents of the context-copying failures in the first phone diagnostic. */
class ContextLeakRegressionTest {
    @Test fun copyingFollowingFragmentCannotCreateDuplicateText() {
        val d = VoiceDraft(); d.finish("I need a list"); d.partial("of groceries")
        val request = d.snapshot()!!
        d.finish("of groceries"); d.finish("It should be numbered")
        assertFalse(d.apply(request, "I need a list of groceries."))
        assertEquals("I need a list of groceries It should be numbered", d.text)
        assertTrue(d.apply(d.snapshot()!!, "I need a list of groceries. It should be numbered."))
    }
    @Test fun singleFollowingWordCannotBePrependedToEarlierSpeech() {
        val d = VoiceDraft(); d.finish("The first word in this sentence"); d.partial("Is")
        val request = d.snapshot()!!
        d.finish("Is still capitalized")
        assertFalse(d.apply(request, "Is the first word in this sentence"))
        assertEquals("The first word in this sentence Is still capitalized", d.text)
        assertTrue(d.apply(d.snapshot()!!, "The first word in this sentence is still capitalized."))
    }
    @Test fun legitimateNewSpeechStaysIntactWhenEarlierPunctuationApplies() {
        val d = VoiceDraft(); d.finish("buy milk"); d.partial("tomorrow")
        val request = d.snapshot()!!
        assertTrue(d.apply(request, "Buy milk."))
        assertEquals("Buy milk. tomorrow", d.text)
    }
    @Test fun rawBudgetCannotBeBypassedByAnEarlierShortenedEdit() {
        val d = VoiceDraft(); d.finish("um buy buy apples")
        assertTrue(d.apply(d.snapshot()!!, "Buy apples."))
        assertNull(d.snapshot(12))
    }
    @Test fun modelMayFormatRecognizedNumberWordsWithoutChangingTheirValues() {
        assertTrue(CleanupGuard.accepts("one apples two bananas", "1. Apples\n2. Bananas"))
        assertTrue(CleanupGuard.accepts("meet at four fifteen", "Meet at 4:15."))
        assertFalse(CleanupGuard.accepts("one apples two bananas", "2. Apples\n1. Bananas"))
        assertFalse(CleanupGuard.accepts("bring two bags", "Bring 3 bags."))
        assertFalse(CleanupGuard.accepts("buy apples bananas", "1. Apples\n2. Bananas"))
    }
}
