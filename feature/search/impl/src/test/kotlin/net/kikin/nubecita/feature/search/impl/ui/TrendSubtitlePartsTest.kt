package net.kikin.nubecita.feature.search.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TrendSubtitlePartsTest {
    @Test
    fun `all three parts come in the order hot, category, count`() {
        assertEquals(
            listOf("Hot", "Science", "48K posts"),
            trendSubtitleParts(hotLabel = "Hot", category = "science", postCountLabel = "48K posts"),
        )
    }

    @Test
    fun `hot only`() {
        assertEquals(listOf("Hot"), trendSubtitleParts(hotLabel = "Hot", category = null, postCountLabel = null))
    }

    @Test
    fun `count only`() {
        assertEquals(listOf("3K posts"), trendSubtitleParts(hotLabel = null, category = null, postCountLabel = "3K posts"))
    }

    @Test
    fun `a blank category is skipped`() {
        assertEquals(
            listOf("3K posts"),
            trendSubtitleParts(hotLabel = null, category = "   ", postCountLabel = "3K posts"),
        )
    }

    @Test
    fun `no parts gives an empty list`() {
        assertEquals(emptyList<String>(), trendSubtitleParts(hotLabel = null, category = "", postCountLabel = null))
    }

    @Test
    fun `the category keeps what the server sent apart from its first letter`() {
        assertEquals(
            listOf("World Cup 2026"),
            trendSubtitleParts(hotLabel = null, category = "world Cup 2026", postCountLabel = null),
        )
    }
}
