package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test
import com.kinetica.keyboard.engine.KineticaConstants as K

class PunctuationTest {
    private fun draft(before: String = "") = VoiceDraft(before, true, "en", FastVoiceFormatting.defaultProtectedWords, true)

    @Test fun pauseForASecondRevisitsTheEarlierUtteranceAndNeverPinsItsQuestionMark() {
        val d = draft()
        d.partial("Whenever I pause for")
        assertTrue(d.applyPunctuation(d.punctuationSnapshot()!!, "Whenever I pause for?"))
        assertEquals("Whenever I pause for", d.text)
        d.finish()
        d.partial("A second")
        assertEquals("whenever i pause for a second", d.punctuationSnapshot()!!.request.input)
        assertTrue(d.applyPunctuation(d.punctuationSnapshot()!!, "Whenever I pause for a second."))
        assertEquals("Whenever I pause for a second", d.text)
        d.finish()
        d.punctuationComplete = true
        assertEquals("Whenever I pause for a second.", d.text)
        assertEquals("Whenever I pause for A second", d.original)
    }

    @Test fun latePunctuationCannotEraseNewWordsOrReplaceAChangedPartial() {
        val d = draft()
        d.partial("Can we meet")
        val old = d.punctuationSnapshot()!!
        d.partial("Can we meet tomorrow")
        assertFalse(d.applyPunctuation(old, "Can we meet?"))
        assertEquals("Can we meet tomorrow", d.text)
        val newer = d.punctuationSnapshot()!!
        d.partial("Can we eat tomorrow")
        assertFalse(d.applyPunctuation(newer, "Can we meet tomorrow?"))
        assertEquals("Can we eat tomorrow", d.text)
    }

    @Test fun appliedPunctuationSurvivesExtensionsButItsOldEndingDoesNotBecomeABoundary() {
        val d = draft()
        d.partial("When I pause for")
        d.applyPunctuation(d.punctuationSnapshot()!!, "When I pause for?")
        d.punctuationComplete = true
        assertEquals("When I pause for?", d.text)
        d.punctuationComplete = false
        d.finish()
        d.partial("A second")
        assertEquals("When I pause for a second", d.text)
        assertTrue(d.applyPunctuation(d.punctuationSnapshot()!!, "When I pause for a second."))
        // A partial extension within a word must not reuse a formatted prefix.
        val other = draft()
        other.partial("For")
        other.applyPunctuation(other.punctuationSnapshot()!!, "For?")
        other.partial("Formatting")
        assertEquals("Formatting", other.text)
    }

    @Test fun richCleanupInvalidatesOldPunctuationAndKeepsListStructureWithNewSpeech() {
        val d = draft()
        d.finish("This is a list one apples two pears")
        val old = d.punctuationSnapshot()!!
        val ai = d.snapshot()!!
        d.partial("And some bananas")
        assertTrue(d.apply(ai, "This is a list.\n1. Apples.\n2. Pears."))
        assertFalse(d.applyPunctuation(old, "This is a list one apples two pears."))
        val current = d.punctuationSnapshot()!!
        assertEquals("And some bananas", current.request.source)
        assertTrue(d.applyPunctuation(current, "This is a list. 1 apples. 2 pears. And some bananas."))
        assertEquals("This is a list.\n1. Apples.\n2. Pears. And some bananas", d.text)
        assertEquals("This is a list one apples two pears And some bananas", d.original)
    }

    @Test fun undoRestoresRawTextAndInvalidatesBothPendingModelEdits() {
        val d = draft()
        d.finish("hello how are you")
        val snapshot = d.punctuationSnapshot()!!
        val ai = d.snapshot()!!
        assertTrue(d.applyPunctuation(snapshot, "Hello, how are you?"))
        assertTrue(d.undo())
        assertEquals("hello how are you", d.text)
        assertFalse(d.punctuationEnabled)
        assertNull(d.punctuationSnapshot())
        assertFalse(d.applyPunctuation(snapshot, "Hello, how are you?"))
        assertFalse(d.apply(ai, "Hello, how are you?"))
    }

    @Test fun readOnlyEditorContextInfluencesTheModelButNeverEntersTheDraft() {
        val d = draft("we agreed")
        d.partial("That we should meet tomorrow")
        val request = d.punctuationSnapshot()!!
        assertEquals("we agreed that we should meet tomorrow", request.request.input)
        assertTrue(d.applyPunctuation(request, "We agreed that we should meet tomorrow."))
        assertEquals("that we should meet tomorrow", d.text)
    }

    @Test fun punctuationOnlyGuardRejectsInventedDroppedChangedOrReorderedWords() {
        val request = PunctuationText.prepare("Meet me at 4:15", "")!!
        for (bad in listOf("Meet me at 5:15.", "Meet me.", "Meet me at 4:15 tomorrow.",
            "Me meet at 4:15.", "Meet, me at 4:15!!", "Meet me at 4:15<script>")) {
            assertNull(bad, PunctuationText.restore(request, bad, emptySet()))
        }
        assertEquals("Meet me at 4:15.", PunctuationText.restore(request, "Meet me at 4:15.", emptySet()))
    }

    @Test fun preserveAcronymsMixedCaseProtectedNamesNumbersApostrophesAndStructure() {
        val source = "I’m meeting NASA and iPhone with Alice at 4:15 p.m. for 3.14 dollars"
        val request = PunctuationText.prepare(source, "")!!
        val out = "I'm meeting Nasa and Iphone with alice at 4:15 p.m. for 3.14 dollars."
        assertEquals(source + ".", PunctuationText.restore(request, out, FastVoiceFormatting.defaultProtectedWords))
        val list = PunctuationText.prepare("Items:\n1. Apples\n2. Pears", "")!!
        assertEquals("Items:\n1. Apples\n2. Pears.", PunctuationText.restore(list, "Items, 1. Apples. 2. Pears.", emptySet()))
    }

    @Test fun contextAndLongWordLimitsProtectTheNativeTokenizer() {
        assertNull(PunctuationText.prepare("x".repeat(K.VOICE_PUNCTUATION_WORD_BYTES + 1), ""))
        assertNull(PunctuationText.prepare("a ".repeat(K.VOICE_PUNCTUATION_CHARS), ""))
        assertNull(PunctuationText.prepare("?!", ""))
        val request = PunctuationText.prepare("speech", "word ".repeat(200))!!
        assertEquals(K.VOICE_PUNCTUATION_CONTEXT_WORDS, request.contextWords)
        assertTrue(request.input.endsWith("speech"))
    }

    @Test fun provisionalEndingsDoNotStripAbbreviationPeriodsOrInventTheirCapitalization() {
        val request = PunctuationText.prepare("meet at 4:15 p.m.", "")!!
        assertEquals("Meet at 4:15 p.m.", PunctuationText.restore(request, "Meet at 4:15 P.m..", emptySet()))
        assertEquals("Meet at 4:15 p.m.", PunctuationText.display("Meet at 4:15 p.m.?", false))
        assertEquals("Meet at 4:15 p.m.", PunctuationText.display("Meet at 4:15 p.m.", false))
    }

    @Test fun allUncleanedUtterancesRemainEditableAcrossSeveralPauses() {
        val d = draft()
        listOf("This works", "When I pause", "For a second", "Can we keep", "The words together").forEach(d::finish)
        assertTrue(d.applyPunctuation(d.punctuationSnapshot()!!,
            "This works when I pause for a second. Can we keep the words together?"))
        d.punctuationComplete = true
        assertEquals("This works when I pause for a second. Can we keep the words together?", d.text)
    }
}
