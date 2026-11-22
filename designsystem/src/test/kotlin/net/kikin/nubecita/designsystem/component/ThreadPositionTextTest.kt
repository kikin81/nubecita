package net.kikin.nubecita.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [withThreadPositionSlot] appends the inline slot the thread pill is drawn
 * into. It must leave the post's own text and its tappable ranges alone.
 */
class ThreadPositionTextTest {
    private val spoken = "Post 3 of 5"

    private fun AnnotatedString.slots() = getStringAnnotations(INLINE_CONTENT_TAG, 0, length)

    @Test
    fun `trailing whitespace is trimmed before the slot`() {
        val result = AnnotatedString("Hello\n\n").withThreadPositionSlot(spoken)

        assertEquals("Hello $spoken", result.text)
        val slot = result.slots().single()
        assertEquals(THREAD_POSITION_INLINE_ID, slot.item)
        assertEquals("Hello ".length, slot.start)
        assertEquals(result.length, slot.end)
    }

    @Test
    fun `a link that ends the text keeps its range and does not cover the slot`() {
        val text = "Read this example.com"
        val linkStart = text.length - "example.com".length
        val source =
            buildAnnotatedString {
                append(text)
                addLink(LinkAnnotation.Url("https://example.com"), linkStart, text.length)
            }

        val result = source.withThreadPositionSlot(spoken)

        val link = result.getLinkAnnotations(0, result.length).single()
        assertEquals(linkStart, link.start)
        assertEquals(text.length, link.end)
        assertEquals(text.length + 1, result.slots().single().start)
    }

    @Test
    fun `empty text is only the slot with no leading space`() {
        val result = AnnotatedString("").withThreadPositionSlot(spoken)

        assertEquals(spoken, result.text)
        assertEquals(0, result.slots().single().start)
    }

    @Test
    fun `whitespace-only text is only the slot`() {
        assertEquals(spoken, AnnotatedString(" \n ").withThreadPositionSlot(spoken).text)
    }

    private companion object {
        // The tag Compose's `appendInlineContent` records its slot under.
        const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"
    }
}
