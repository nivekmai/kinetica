package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceDraftTest {
    @Test fun previouslyMergedUtterancesNeverDisappearFromTheNextRequest() {
        val d = VoiceDraft()
        val older = "earlier words ".repeat(90).trim()
        d.finish(older)
        assertTrue(d.apply(d.snapshot()!!, older))
        d.finish("The sentence should and with a period,not a comma")
        val request = d.snapshot()!!
        assertTrue(request.input.startsWith(older))
        assertTrue(request.input.endsWith("a comma"))
        assertTrue(d.apply(request, older + " The sentence should end with a period, not a comma."))
        assertTrue(d.text.endsWith("should end with a period, not a comma."))
    }

    @Test fun overlappingUtterancesDoNotFeedOldCapitalsBackToTheModel() {
        val d = VoiceDraft()
        d.finish("it gets Confused")
        val first = d.snapshot()!!
        d.finish("Before it finishes")
        d.partial("and I keep speaking")
        assertTrue(d.apply(first, "it gets confused"))
        val second = d.snapshot()!!
        assertEquals("it gets confused Before it finishes", second.input)
        assertEquals("it gets Confused Before it finishes", second.original)
        assertTrue(d.apply(second, "it gets confused before it finishes"))
        d.finish("and I keep speaking")
        assertEquals("it gets confused before it finishes and I keep speaking", d.snapshot()!!.input)
        assertTrue(d.undo())
        assertEquals("it gets Confused Before it finishes and I keep speaking", d.text)
    }
    @Test fun onlyTheModelChangesCasingAndTimesAndUndoRestoresSpeech() {
        val d = VoiceDraft()
        d.finish("we are Doing this at 4 15 PM")
        val request = d.snapshot()!!
        assertEquals("we are Doing this at 4 15 PM", d.text)
        assertEquals(d.text, request.input)
        d.partial("And I am still recording")
        assertTrue(d.apply(request, "We are doing this at 4:15 PM."))
        assertEquals("We are doing this at 4:15 PM. And I am still recording", d.text)
        assertTrue(d.undo())
        assertEquals("we are Doing this at 4 15 PM And I am still recording", d.text)
    }
    @Test fun allUtterancesAreEditableButTextBeforeDictationIsReadOnly() {
        val d = VoiceDraft("I was waiting because")
        d.finish("It was late"); d.finish("And I cut the confirmation email"); d.partial("from the hotel")
        val snapshot = d.snapshot()!!
        assertEquals("It was late And I cut the confirmation email", snapshot.input)
        assertEquals("I was waiting because", snapshot.context.before)
        assertEquals("from the hotel", snapshot.context.after)
        assertTrue(d.apply(snapshot, "it was late and I got the confirmation email"))
        assertEquals("it was late and I got the confirmation email from the hotel", d.text)
        assertFalse(d.text.contains("I was waiting because"))
    }
    @Test fun laterSegmentsLetCleanupRevisitStrayCapitalsAndMisheardWords() {
        val d = VoiceDraft()
        d.finish("I finally cut"); d.finish("The confirmation email from the hotel")
        assertTrue(d.apply(d.snapshot()!!, "I finally got the confirmation email from the hotel."))
        assertEquals("I finally got the confirmation email from the hotel.", d.text)
        assertTrue(CleanupGuard.accepts("I cut the paper with scissors", "I cut the paper with scissors."))
        assertTrue(d.undo())
        assertEquals("I finally cut The confirmation email from the hotel", d.text)
    }
    @Test fun earlierCleanedTextStaysEditableInsteadOfBecomingReadOnlyContext() {
        val d = VoiceDraft("a".repeat(500))
        d.finish("it was late")
        assertTrue(d.apply(d.snapshot()!!, "It was late."))
        d.finish("And")
        val snapshot = d.snapshot()!!
        assertEquals(com.kinetica.keyboard.engine.KineticaConstants.VOICE_CONTEXT_CHARS, snapshot.context.before.length)
        assertEquals("It was late. And", snapshot.input)
    }
    @Test fun partialsReplaceRatherThanDuplicate() {
        val d = VoiceDraft()
        d.partial("buy apple"); d.partial("buy apples"); d.finish("buy apples and milk")
        assertEquals("buy apples and milk", d.text)
        d.partial("tomorrow")
        assertEquals("buy apples and milk tomorrow", d.text)
    }
    @Test fun cleanupCannotEraseSpeechThatArrivedWhileItRan() {
        val d = VoiceDraft()
        d.finish("um buy buy apples")
        val snapshot = d.snapshot()!!
        d.finish("and milk"); d.partial("tomorrow")
        assertTrue(d.apply(snapshot, "Buy apples."))
        assertEquals("Buy apples. and milk tomorrow", d.text)
        assertEquals("um buy buy apples and milk tomorrow", d.original)
        assertTrue(d.undo())
        assertEquals(d.original, d.text)
    }
    @Test fun clearAndUndoInvalidateLateCleanup() {
        val d = VoiceDraft()
        d.finish("hello"); val old = d.snapshot()!!; d.clear(); d.finish("hello")
        assertFalse(d.apply(old, "Hello."))
        assertTrue(d.apply(d.snapshot()!!, "Hello."))
        val beforeUndo = d.snapshot()!!
        d.undo()
        assertFalse(d.apply(beforeUndo, "Hello!"))
    }
    @Test fun oversizedSessionIsExplicitlyRefusedInsteadOfEditingOnlyNewestUtterance() {
        val d = VoiceDraft()
        d.finish("first utterance"); d.finish("second"); d.partial("unfinished")
        assertNull(d.snapshot(10))
        assertEquals("first utterance second", d.snapshot()!!.input)
        assertEquals("unfinished", d.snapshot()!!.context.after)
    }
    @Test fun preservesPartialWhenRecognizerReturnsEmptyFinal() {
        val d = VoiceDraft(); d.partial("keep these words"); d.finish("")
        assertEquals("keep these words", d.text)
        assertEquals("", d.partial)
    }
    @Test fun allowsActualCleanupAndSpokenCorrections() {
        assertTrue(CleanupGuard.accepts("um I I will come Tuesday sorry Wednesday", "I will come Wednesday."))
        assertTrue(CleanupGuard.accepts("dont forget Chloé", "Don’t forget Chloé."))
        assertTrue(CleanupGuard.accepts("buy apples bananas and milk", "Buy:\n- apples\n- bananas\n- milk"))
        assertTrue(CleanupGuard.accepts("bring 10 actually 12 folders", "Bring 12 folders."))
        assertFalse(CleanupGuard.accepts("bring 10 folders", "Bring 20 folders."))
        assertFalse(CleanupGuard.accepts("explain photosynthesis", "Plants convert sunlight into chemical energy."))
        assertFalse(CleanupGuard.accepts("hello", "hello\u0000"))
        assertFalse(CleanupGuard.accepts("buy three things apples bananas and milk",
            "Buy three apples, three bananas and three bottles of milk."))
        assertFalse(CleanupGuard.accepts("call at 10 and bring 12 folders", "Call at 12 and bring 10 folders."))
    }
    @Test fun rejectsRetainingTheAbandonedDateInAnExplicitCorrection() {
        val raw = "tell Morgan that um the draft is ready today sorry tomorrow"
        assertFalse(CleanupGuard.accepts(raw, "Tell Morgan that the draft is ready today."))
        assertTrue(CleanupGuard.accepts(raw, "Tell Morgan that the draft is ready tomorrow."))
    }
    @Test fun suffixEditPreservesEarlierTextAndHandlesShrinkingPartials() {
        val edit = VoiceEdit.between("Buy apples and pears", "Buy apples.")
        assertEquals(10, edit.deleteCount)
        assertEquals(".", edit.insert)
        assertTrue(edit.matches("Earlier text. Buy apples and pears"))
        assertFalse(edit.matches("Changed text Buy apples and grapes"))
    }
    @Test fun suffixEditNeverSplitsSurrogatePair() {
        val edit = VoiceEdit.between("hi 😁", "hi 😂")
        assertEquals(2, edit.deleteCount)
        assertEquals("😂", edit.insert)
    }
}
