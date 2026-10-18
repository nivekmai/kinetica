package com.kinetica.keyboard.voice

import com.kinetica.keyboard.keys.*
import com.kinetica.keyboard.settings.ShortcutKinds
import com.kinetica.keyboard.settings.ShortcutKind
import org.junit.Assert.*
import org.junit.Test

class VoiceShortcutTest {
    @Test fun namedEnterAlternatesDispatchActionsWithoutChangingLiteralPunctuation() {
        val cells = com.kinetica.keyboard.settings.KeyboardConfig.parseEnterAlternates(":voice_typing :paste ?")
        assertEquals(EditorAction.VOICE_TYPING, ActionRow.actionForAlternate(cells[0], false))
        assertEquals(EditorAction.PASTE, ActionRow.actionForAlternate(cells[1], false))
        assertNull(ActionRow.actionForAlternate(cells[2], false))
        assertEquals(ActionRow.glyph(EditorAction.VOICE_TYPING), ActionRow.alternateLabel(cells[0]))
        assertEquals(":voice_typing", cells[0])
    }
    @Test fun explicitActionsWorkOnPunctuationButBareSymbolsStayLiteral() {
        for (isLetter in listOf(false, true)) {
            assertEquals(EditorAction.COPY, ActionRow.actionForAlternate(":copy", isLetter))
            assertEquals(EditorAction.SELECT_ALL, ActionRow.actionForAlternate(":select_all", isLetter))
        }
        for (literal in listOf("→", ",", ":", "\n", ":unknown"))
            assertNull(ActionRow.actionForAlternate(literal, false))
        assertEquals(EditorAction.VOICE_TYPING,
            ActionRow.actionForAlternate(ActionRow.glyph(EditorAction.VOICE_TYPING), true))
    }

    @Test fun voiceIsSelectableAndRoundTripsAcrossEveryShortcutPicker() {
        val kind = ShortcutKind.Action(EditorAction.VOICE_TYPING)
        for (choices in listOf(ShortcutKinds.chords, ShortcutKinds.edgeSwipes, ShortcutKinds.expansions))
            assertTrue(choices.contains(kind))
        assertEquals("action:voice_typing", ShortcutKinds.encode(kind, ""))
        assertEquals(kind, ShortcutKinds.decode("action:voice_typing").first)
        assertTrue(ActionRow.ALL.contains(EditorAction.VOICE_TYPING))
        assertEquals(ActionRow.glyph(EditorAction.VOICE_TYPING), ActionRow.glyphForToken(":voice_typing"))
    }
    @Test fun promotingVoiceRetainsEveryExistingShortcutAndTheirRelativeOrder() {
        val set = ShortcutSet(listOf(EditorAction.PASTE, EditorAction.COPY))
        val voice = EditorAction.VOICE_TYPING
        set.toggle(voice)
        while (set.canMove(voice, -1)) set.move(voice, -1)
        assertEquals(listOf(voice, EditorAction.PASTE, EditorAction.COPY),
            set.rows().filter { it.second }.map { it.first })
        assertEquals(set.rows(), ShortcutSet.from(set.selection(), set.order()).rows())
    }
}
