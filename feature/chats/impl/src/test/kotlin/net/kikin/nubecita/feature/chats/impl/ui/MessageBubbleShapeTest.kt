package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MessageBubbleShapeTest {
    private val large = 16.dp
    private val small = 4.dp

    @Test
    fun `single outgoing - all corners large`() {
        val shape = messageBubbleShape(index = 0, count = 1, isOutgoing = true)
        assertEquals(RoundedCornerShape(large, large, large, large), shape)
    }

    @Test
    fun `single incoming - all corners large`() {
        val shape = messageBubbleShape(index = 0, count = 1, isOutgoing = false)
        assertEquals(RoundedCornerShape(large, large, large, large), shape)
    }

    @Test
    fun `outgoing first of 3 - bottom-end small, others large`() {
        val shape = messageBubbleShape(index = 0, count = 3, isOutgoing = true)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = small, bottomStart = large), shape)
    }

    @Test
    fun `outgoing middle of 3 - both end-side corners small`() {
        val shape = messageBubbleShape(index = 1, count = 3, isOutgoing = true)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = small, bottomEnd = small, bottomStart = large), shape)
    }

    @Test
    fun `outgoing last of 3 - top-end small, others large`() {
        val shape = messageBubbleShape(index = 2, count = 3, isOutgoing = true)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = small, bottomEnd = large, bottomStart = large), shape)
    }

    @Test
    fun `incoming first of 3 - bottom-start small, others large`() {
        val shape = messageBubbleShape(index = 0, count = 3, isOutgoing = false)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = large, bottomStart = small), shape)
    }

    @Test
    fun `incoming middle of 3 - both start-side corners small`() {
        val shape = messageBubbleShape(index = 1, count = 3, isOutgoing = false)
        assertEquals(RoundedCornerShape(topStart = small, topEnd = large, bottomEnd = large, bottomStart = small), shape)
    }

    @Test
    fun `incoming last of 3 - top-start small, others large`() {
        val shape = messageBubbleShape(index = 2, count = 3, isOutgoing = false)
        assertEquals(RoundedCornerShape(topStart = small, topEnd = large, bottomEnd = large, bottomStart = large), shape)
    }
}
