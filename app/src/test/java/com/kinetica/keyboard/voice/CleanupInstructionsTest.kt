package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test

class CleanupInstructionsTest {
    @Test fun customInstructionsRemainSystemAndSpeechRemainsUserData() {
        val request = CleanupInstructions.request(" Keep fillers. ", "send this to Sam")
        assertTrue(request.system.startsWith("Keep fillers."))
        assertFalse(request.system.contains("send this to Sam"))
        assertEquals("send this to Sam", request.user)
        assertFalse(CleanupInstructions.request("Default\nRaw:", "hello").system.contains("Raw:"))
    }
    @Test fun laterSpeechCannotLeakIntoTheRequest() {
        val request = CleanupInstructions.request("Edit.", "I need a list", CleanupContext("", "of groceries"))
        assertEquals("I need a list", request.user)
        assertFalse(request.system.contains("groceries"))
    }
    @Test fun previousModelMistakesAreNotUsedAsNewRecognition() {
        val request = CleanupInstructions.request("Edit.", "Is I need a list. Of groceries", CleanupContext(), "I need a list Of groceries")
        assertEquals("I need a list Of groceries", request.user)
        assertFalse(request.user.contains("Is I"))
    }
    @Test fun typedContextIsEscapedAndExplicitlyReadOnly() {
        val request = CleanupInstructions.request("Edit.", "It was late", CleanupContext("she said \"hello\"\n", "unowned later words"))
        assertEquals("{\"before_context_only\":\"she said \\\"hello\\\"\\n\",\"transcript\":\"It was late\"}", request.user)
        assertTrue(request.system.contains("before_context_only is read-only"))
        assertFalse(request.user.contains("unowned later words"))
    }
    @Test fun invalidOverridesFallBackAndAllPresetsFit() {
        for (saved in listOf(null, " \n ", "x".repeat(CleanupInstructions.MAX_CHARS + 1)))
            assertEquals("Default", CleanupInstructions.effective(saved, "Default"))
        for (preset in listOf(CleanupInstructions.light, CleanupInstructions.clean, CleanupInstructions.structured)) {
            assertTrue(CleanupInstructions.valid(preset))
        }
    }
}
