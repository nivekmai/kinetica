package com.kinetica.keyboard.ime

import com.kinetica.keyboard.keys.ActionRow
import com.kinetica.keyboard.keys.EditorAction
import org.junit.Assert.*
import org.junit.Test

/** Guards the integration seams moved by v3; actual IME focus/visibility needs a phone. */
class ForkV3IntegrationTest {
    private fun body(signature: String): String {
        val src = ImeSources.declaring(signature).text
        val start = src.indexOf(signature)
        return src.substring(start, src.indexOf("\n    }", start))
    }

    @Test fun localVoiceAndGifsSurviveV3AlternateCellConversion() {
        for (action in listOf(EditorAction.VOICE_TYPING, EditorAction.GIFS)) {
            val token = ":${action.name.lowercase()}"
            assertEquals(action, ActionRow.actionForGlyph(AlphaLayouts.cell(token)))
            assertEquals(action, ActionRow.actionForGlyph(AlphaLayouts.cell(action.output)))
        }
        assertEquals(EditorAction.DICTATE, ActionRow.actionForGlyph(AlphaLayouts.cell(":dictate")))
        assertEquals("?", AlphaLayouts.cell("?"))
        assertNull(ActionRow.actionForGlyph(AlphaLayouts.cell("→")))
    }

    @Test fun forkPanelsEndEmojiSearchBeforeShowingTheirOwnUi() {
        for (name in listOf("openVoiceTyping", "openNavigation", "openGifs")) {
            val b = body("fun $name()")
            assertTrue("$name leaves emoji search consuming keyboard input", b.contains("emojiSearch.close("))
            assertTrue(b.indexOf("emojiSearch.close(") < b.indexOf("views.closeEmojiPicker()"))
        }
        val search = body("fun open(src: String)")
        for (close in listOf("closeGifs", "closeNavigation", "closeVoiceTyping")) {
            assertTrue(search.indexOf("ime.$close()") in 0 until search.indexOf("container.showEmojiSearch("))
        }
    }

    @Test fun typingHistoryFollowsTheNewCommitPathIndependentlyOfLiveSpeed() {
        val speed = body("fun recordTypingSpeed(word: String)")
        assertTrue(speed.indexOf("ime.recordTypingHistory(") in 0 until speed.indexOf("!ime.config.typingSpeed"))
        assertTrue(body("fun abandonWord()").contains("ime.typingHistory.discardWord()"))
        val token = body("fun onTokenFinalized(token: InputToken)")
        assertTrue(token.indexOf("if (emojiSearch.active)") < token.indexOf("typingHistory.touch("))
        assertTrue(token.contains("!editorState.teachesNothing"))
    }
}
