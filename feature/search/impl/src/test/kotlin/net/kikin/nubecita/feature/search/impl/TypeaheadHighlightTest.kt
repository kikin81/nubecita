package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeaheadHighlightTest {
    @Test
    fun `query al does not highlight inside bsky_social`() {
        // "al" is also inside "social"; only the name part may light up.
        assertEquals(
            listOf(0 until 2),
            highlightRanges(text = "alice.bsky.social", query = "al", isHandle = true),
        )
    }

    @Test
    fun `query ali highlights the name part of alice_bsky_social`() {
        assertEquals(
            listOf(0 until 3),
            highlightRanges(text = "alice.bsky.social", query = "ali", isHandle = true),
        )
    }

    @Test
    fun `a custom-domain handle is highlighted in full`() {
        // Not a .bsky.social handle, so a match anywhere in it counts, including later labels.
        assertEquals(
            listOf(6 until 10),
            highlightRanges(text = "alice.example.com", query = "exam", isHandle = true),
        )
        assertEquals(
            listOf(0 until 2, 11 until 13),
            highlightRanges(text = "al.example.al", query = "al", isHandle = true),
        )
    }

    @Test
    fun `a query with a dot is matched against the full handle`() {
        assertEquals(
            listOf(0 until 10),
            highlightRanges(text = "alice.bsky.social", query = "alice.bsky", isHandle = true),
        )
        assertEquals(
            listOf(0 until 17),
            highlightRanges(text = "alice.bsky.social", query = "alice.bsky.social", isHandle = true),
        )
    }

    @Test
    fun `a query matching only the suffix highlights nothing`() {
        assertEquals(
            emptyList<IntRange>(),
            highlightRanges(text = "alice.bsky.social", query = "social", isHandle = true),
        )
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(
            listOf(0 until 3),
            highlightRanges(text = "Alice.bsky.social", query = "ALI", isHandle = true),
        )
    }

    @Test
    fun `a blank query highlights nothing`() {
        assertEquals(emptyList<IntRange>(), highlightRanges(text = "alice.bsky.social", query = " ", isHandle = true))
    }

    @Test
    fun `a display name is never restricted`() {
        assertEquals(
            listOf(0 until 2),
            highlightRanges(text = "Alice", query = "al", isHandle = false),
        )
    }
}
