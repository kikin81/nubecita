package net.kikin.nubecita.wear.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextLengthTest {
    @Test
    fun countsGraphemes_notUtf16Units() {
        // Each runner is one grapheme but two UTF-16 units.
        assertEquals(3, TextLength.of("🏃🏃!", max = 300).graphemes)
    }

    @Test
    fun exactlyTheLimit_canBeSent_oneOver_cannot() {
        val atLimit = TextLength.of("a".repeat(300), max = 300)
        val over = TextLength.of("a".repeat(301), max = 300)

        assertTrue(atLimit.canSend)
        assertFalse(atLimit.isOverLimit)
        assertFalse(over.canSend)
        assertTrue(over.isOverLimit)
    }

    @Test
    fun blankText_cannotBeSent_andSurroundingSpaceDoesNotCount() {
        assertFalse(TextLength.of("   ", max = 300).canSend)
        assertEquals(2, TextLength.of("  hi  ", max = 300).graphemes)
    }
}
