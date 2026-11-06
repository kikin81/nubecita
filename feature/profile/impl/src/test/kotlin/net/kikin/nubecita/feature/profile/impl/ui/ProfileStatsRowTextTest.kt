package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ProfileStatsRowTextTest {
    private val style = SpanStyle(fontWeight = FontWeight.Bold)

    @Test
    fun `the count is emphasised and the label isn't`() {
        val span = emphasizeCount("420 Followers", "420", style).spanStyles.single()

        assertEquals(0 to 3, span.start to span.end)
    }

    @Test
    fun `a locale can put the count after the label`() {
        val span = emphasizeCount("Seguidores: 1,2 mil", "1,2 mil", style).spanStyles.single()

        assertEquals(12 to 19, span.start to span.end)
    }

    @Test
    fun `text without the count stays plain`() {
        val text = emphasizeCount("Followers", "420", style)

        assertEquals("Followers", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }
}
