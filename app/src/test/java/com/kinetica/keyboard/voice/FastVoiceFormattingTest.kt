package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test

class FastVoiceFormattingTest {
    @Test fun titleCaseIsLoweredByDefaultWithoutAnAllowlistOrAnAiJob() {
        val d = VoiceDraft(fastFormatting = true)
        val raw = "It seems like it is still Taking a Long time to Clean up the capitalizations"
        d.partial(raw)
        assertEquals("It seems like it is still taking a long time to clean up the capitalizations", d.text)
        assertEquals(raw, d.original)
        for (word in listOf("Need", "Maintained", "Unexpected", "Complicated", "Zorpified")) {
            assertEquals("we ${word.lowercase()}", FastVoiceFormatting.format("we $word", "because", "en"))
        }
    }

    @Test fun newUtteranceOpeningUsesPreviousTextDuringPartialsAndLateCleanup() {
        val d = VoiceDraft(fastFormatting = true)
        d.finish("It is still")
        val older = d.snapshot()!!
        d.partial("Taking"); assertEquals("It is still taking", d.text)
        d.partial("Taking a Long time"); assertEquals("It is still taking a long time", d.text)
        d.finish()
        d.partial("To Clean it")
        assertEquals("It is still taking a long time to clean it", d.text)
        assertTrue(d.apply(older, "It is still."))
        assertEquals("It is still. Taking a long time to clean it", d.text)
        val newer = d.snapshot()!!
        assertTrue(d.apply(newer, "It is still Taking a Long time"))
        assertEquals("It is still taking a long time to clean it", d.text)
        assertTrue(d.undo())
        assertEquals("It is still Taking a Long time To Clean it", d.text)
    }

    @Test fun userDenylistProtectsNamesAndPossessivesWithoutProtectingOtherWords() {
        val protected = FastVoiceFormatting.protectedWords("Zelda\n ACME, Zelda\nO’Connor")
        assertEquals(setOf("zelda", "acme", "o'connor"), protected)
        val raw = "we saw Zelda and O’Connor at Acme Taking a Long walk with Zelda's dog"
        assertEquals("we saw Zelda and O’Connor at Acme taking a long walk with Zelda's dog",
            FastVoiceFormatting.format(raw, "because", "en", protected))
        assertEquals("we saw zelda at acme", FastVoiceFormatting.format("we saw Zelda at Acme", "because", "en", emptySet()))
        assertEquals("because iPhone and NASA work with I and I'm", FastVoiceFormatting.format(
            "because iPhone and NASA work with i and i'm", "earlier", "en", emptySet()))
    }

    @Test fun emptyDenylistReplacesDefaultsAndDoesNotLeakBetweenSessions() {
        assertTrue(FastVoiceFormatting.protectedWords(null).contains("alice"))
        assertTrue(FastVoiceFormatting.protectedWords("").isEmpty())
        val protected = VoiceDraft("because", true, "en", FastVoiceFormatting.protectedWords("Zelda"))
        val unprotected = VoiceDraft("because", true, "en", emptySet())
        for (d in listOf(protected, unprotected)) d.partial("Zelda called")
        assertEquals("Zelda called", protected.text)
        assertEquals("zelda called", unprotected.text)
        assertEquals("May called", FastVoiceFormatting.format("may called", "", "en"))
    }

    @Test fun denylistIsAppliedToAiOutputWithoutWaitingForAnotherCleanup() {
        val d = VoiceDraft(fastFormatting = true, protectedWords = FastVoiceFormatting.protectedWords("Zelda"))
        val raw = "I met Zelda and it is Taking a Long time to Clean up"
        d.finish(raw)
        assertTrue(d.apply(d.snapshot()!!, "$raw."))
        assertEquals("I met Zelda and it is taking a long time to clean up.", d.text)
        assertEquals(raw, d.original)
    }

    @Test fun punctuationStartsSentencesButRecognitionBoundariesDoNot() {
        val d = VoiceDraft(fastFormatting = true)
        d.finish("we are"); d.finish("Taking a Long time."); d.partial("clean up")
        assertEquals("We are taking a long time. Clean up", d.text)
        assertEquals("Tomorrow", FastVoiceFormatting.format("tomorrow", "at 4 p.m.\n", "en"))
        assertEquals("We meet at 4:15 p.m. tomorrow.", FastVoiceFormatting.format("we meet at 4:15 p.m. tomorrow.", "", "en"))
    }

    @Test fun recognitionBoundaryCapitalsAreLoweredWhileOriginalRemainsExact() {
        val d = VoiceDraft(fastFormatting = true)
        d.finish("I was relieved because"); d.finish("It arrived"); d.partial("And I could leave")
        assertEquals("I was relieved because it arrived and I could leave", d.text)
        assertEquals("I was relieved because It arrived And I could leave", d.original)
        d.finish()
        assertEquals("I was relieved because it arrived and I could leave", d.snapshot()!!.input)
        assertEquals(d.original, d.snapshot()!!.original)
    }

    @Test fun asynchronousCleanupRecomputesNewerJoinsAndUndoRestoresRawSpeech() {
        val d = VoiceDraft(fastFormatting = true)
        d.finish("I was relieved"); val old = d.snapshot()!!
        d.finish("It arrived"); d.partial("And I could leave")
        assertEquals("I was relieved it arrived and I could leave", d.text)
        assertTrue(d.apply(old, "I was relieved."))
        assertEquals("I was relieved. It arrived and I could leave", d.text)
        val next = d.snapshot()!!
        assertTrue(d.apply(next, "I was relieved it arrived"))
        assertEquals("I was relieved it arrived and I could leave", d.text)
        d.finish(); val late = d.snapshot()!!
        assertTrue(d.undo())
        assertEquals("I was relieved It arrived And I could leave", d.text)
        assertFalse(d.fastFormatting)
        assertFalse(d.apply(late, "I was relieved it arrived and I could leave."))
    }

    @Test fun keepsSentenceStartsNamesPronounsAcronymsAndQuotedWords() {
        for (before in listOf("", "Done.", "Why?", "Fine!", "Items:", "Done.\"", "a line\n"))
            assertEquals("It works", FastVoiceFormatting.format("It works", before, "en-US"))
        for (text in listOf("I agree", "I'm ready", "NASA called", "IT works", "May called", "Will called", "Alice called", "\"It\" is quoted"))
            assertEquals(text, FastVoiceFormatting.format(text, "because", "en-US"))
        assertEquals("it works", FastVoiceFormatting.format("It works", "because", "en-US"))
        assertEquals("it's here", FastVoiceFormatting.format("It's here", "because", "en-US"))
        assertEquals("It works", FastVoiceFormatting.format("It works", "because", "it-IT"))
    }

    @Test fun commaSpacingIsImmediateButNumbersTimesAndWordsStayUntouched() {
        val d = VoiceDraft(fastFormatting = true)
        d.partial("apples ,bananas,pears cost 1,000 at 4 15 PM")
        assertEquals("Apples, bananas, pears cost 1,000 at 4 15 PM", d.text)
        assertTrue(d.canUndo)
        assertTrue(d.undo())
        assertEquals(d.original, d.text)
        assertFalse(d.canUndo)
        assertEquals("I cut the paper", FastVoiceFormatting.format("I cut the paper", "", "en"))
    }

    @Test fun interiorCapitalsAndContractionsAreFixedWithinOneRecognitionCallback() {
        val d = VoiceDraft(fastFormatting = true)
        val raw = "It's also Regularly missing capitalizations so I'm not sure What's going on with that and They're sneaking in."
        d.partial(raw)
        assertEquals("It's also regularly missing capitalizations so I'm not sure what's going on with that and they're sneaking in.", d.text)
        assertEquals(raw, d.original)
        d.finish()
        assertEquals(d.text, d.snapshot()!!.input)
        assertTrue(d.undo())
        assertEquals(raw, d.text)
    }

    @Test fun modelResultCannotReintroduceCommonMidSentenceCapitalsOrMissingSentenceStarts() {
        val d = VoiceDraft(fastFormatting = true)
        d.finish("can we meet tomorrow or would Friday be better")
        val first = d.snapshot()!!
        d.finish("And we can discuss it")
        assertTrue(d.apply(first, "can we meet tomorrow, Or would Friday be better?"))
        assertEquals("Can we meet tomorrow, or would Friday be better? And we can discuss it", d.text)
        val next = d.snapshot()!!
        assertTrue(d.apply(next, "can we meet tomorrow, Or would Friday be better And we can discuss it?"))
        assertEquals("Can we meet tomorrow, or would Friday be better and we can discuss it?", d.text)
        assertTrue(d.undo())
        assertEquals("can we meet tomorrow or would Friday be better And we can discuss it", d.text)
    }

    @Test fun clearSentenceAndListStartsAreCapitalizedWithoutChangingNamesQuotesOrTimes() {
        assertEquals("It works. What next? We can ask May and Will at NASA.",
            FastVoiceFormatting.format("it works. what next? we can ask May And Will at NASA.", "", "en"))
        assertEquals("Items:\n1. The first item.\n2. The second item.",
            FastVoiceFormatting.format("items:\n1. the first item.\n2. the second item.", "", "en"))
        val raw = "Ask Dr. May at 4:15 p.m. tomorrow about \"The Who And Us\" and IT."
        assertEquals(raw, FastVoiceFormatting.format(raw, "", "en"))
        assertEquals("The Who\" and May", FastVoiceFormatting.format("The Who\" And May", "I said \"", "en"))
        assertEquals("tomorrow", FastVoiceFormatting.format("tomorrow", "meet at 4:15 p.m.", "en"))
        assertEquals("iPhone works. eBay too.", FastVoiceFormatting.format("iPhone works. eBay too.", "", "en"))
        assertEquals("This costs 1,000 at 4 15 PM", FastVoiceFormatting.format("this costs 1,000 at 4 15 PM", "", "en"))
    }

    @Test fun switchingOffAndOtherLanguagesPreserveRecognition() {
        for (d in listOf(VoiceDraft(fastFormatting = false), VoiceDraft(fastFormatting = true, language = "de-DE"))) {
            d.finish("earlier words"); d.finish("And more,words")
            assertEquals(d.original, d.text)
        }
    }

    @Test fun fastFormattingDoesNotConsumeNewPartialsOrChangeReadOnlyContext() {
        val d = VoiceDraft("we were waiting because", true)
        d.partial("It came,finally"); d.partial("It arrived,finally")
        assertEquals("it arrived, finally", d.text)
        d.finish("")
        assertEquals("It arrived,finally", d.original)
        assertEquals("we were waiting because", d.snapshot()!!.context.before)
        assertTrue(d.apply(d.snapshot()!!, "it arrived, finally."))
        assertEquals("it arrived, finally.", d.text)
    }
}
