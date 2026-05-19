package net.kikin.nubecita.feature.moderation.impl.data.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Spec coverage: the repository MUST truncate `details` to a maximum of
 * 2000 graphemes before submitting `com.atproto.moderation.createReport`.
 * The UI's 300-grapheme cap is enforced elsewhere; this helper is the
 * server-contract truncation.
 *
 * Mirrors [feature.composer.impl.internal.GraphemeCounter]'s
 * BreakIterator approach. If that helper graduates to `:core:common`,
 * this one collapses into it.
 */
class GraphemeTextTest {
    @Test
    fun truncateReturnsTheSameStringWhenShorterThanMax() {
        assertEquals("hello", GraphemeText.truncate("hello", max = 100))
    }

    @Test
    fun truncateReturnsTheSameStringWhenExactlyAtMax() {
        assertEquals("hello", GraphemeText.truncate("hello", max = 5))
    }

    @Test
    fun truncateCutsToTheGraphemeCount() {
        assertEquals("hel", GraphemeText.truncate("hello", max = 3))
    }

    @Test
    fun truncateHandlesEmptyString() {
        assertEquals("", GraphemeText.truncate("", max = 10))
        assertEquals("", GraphemeText.truncate("", max = 0))
    }

    @Test
    fun truncateAtZeroReturnsEmpty() {
        assertEquals("", GraphemeText.truncate("hello", max = 0))
    }

    @Test
    fun truncateRespectsSurrogatePairsForBmpExtendedEmoji() {
        // 🎉 is 1 codepoint above the BMP, encoded as 2 UTF-16 code
        // units. `text.length` reports 2; grapheme count is 1. A naive
        // `text.take(1)` would split inside the surrogate pair and
        // leave a lone surrogate in the wire payload.
        val emoji = "🎉"
        val text = "$emoji hello"
        // 1 grapheme = the whole emoji
        assertEquals(emoji, GraphemeText.truncate(text, max = 1))
        // 3 graphemes = emoji + space + "h"
        assertEquals("$emoji h", GraphemeText.truncate(text, max = 3))
    }
}
