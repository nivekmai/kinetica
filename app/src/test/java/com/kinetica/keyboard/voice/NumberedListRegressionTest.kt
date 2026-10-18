package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test

class NumberedListRegressionTest {
    private val raw = "This is a test of simple things let's make a list One the first item Two the second item Three the third item"
    private val formatted = "This is a test of simple things. Let's make a list.\n\n1. The first item.\n2. The second item.\n3. The third item."

    @Test fun numberedListLayoutAndIntroductionSurviveApplicationAndFurtherSpeech() {
        val d = VoiceDraft(fastFormatting = true)
        d.finish("This is a test of simple things let's make a list")
        d.finish("One the first item"); d.finish("Two the second item"); d.finish("Three the third item")
        val snapshot = d.snapshot()!!
        assertEquals(raw, snapshot.original)
        d.partial("And that is all")
        assertTrue(d.apply(snapshot, formatted))
        assertEquals("$formatted And that is all", d.text)
        assertTrue(d.undo())
        assertEquals("$raw And that is all", d.text)
    }

    @Test fun listOnlyOutputCannotSilentlyDiscardTheIntroduction() {
        assertFalse(CleanupGuard.accepts(raw, "1. The first item\n2. The second item\n3. The third item"))
        assertFalse(CleanupGuard.accepts(raw, "1. One\n2. Two\n3. Three"))
        assertTrue(CleanupGuard.accepts(raw, formatted))
        assertFalse(CleanupGuard.accepts(raw, formatted.replace("2.", "4.")))
    }

    @Test fun coverageStillAllowsRepeatedFillersAndClearWordCorrections() {
        assertTrue(CleanupGuard.accepts("um uh um hello hello", "Hello."))
        assertTrue(CleanupGuard.accepts("the sentence should and with a period", "The sentence should end with a period."))
        assertTrue(CleanupGuard.accepts("I finally cut the confirmation email", "I finally got the confirmation email."))
        assertTrue(CleanupGuard.accepts("bring 10 actually 12 folders", "Bring 12 folders."))
        assertTrue(CleanupGuard.accepts("come Tuesday sorry Wednesday", "Come Wednesday."))
    }
}
