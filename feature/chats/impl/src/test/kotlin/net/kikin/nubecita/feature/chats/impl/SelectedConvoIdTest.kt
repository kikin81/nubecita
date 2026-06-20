package net.kikin.nubecita.feature.chats.impl

import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit coverage for [selectedConvoId] — the pure back-stack → open-convo
 * derivation that drives the list pane's selected-row highlight in the
 * tablet list-detail layout. Logic lives in a testable helper (not inlined
 * in the nav-module Composable) per the "assert logic in unit tests, not
 * screenshots" layering convention.
 */
internal class SelectedConvoIdTest {
    @Test
    fun `returns null when no Chat thread is on the back stack`() {
        assertNull(selectedConvoId(listOf(Chats)))
    }

    @Test
    fun `returns null for an empty back stack`() {
        assertNull(selectedConvoId(emptyList()))
    }

    @Test
    fun `returns the convoId of the open Chat thread`() {
        assertEquals("convo-1", selectedConvoId(listOf(Chats, Chat(convoId = "convo-1"))))
    }

    @Test
    fun `returns the topmost Chat convoId when several are stacked`() {
        assertEquals("convo-2", selectedConvoId(listOf(Chats, Chat(convoId = "convo-1"), Chat(convoId = "convo-2"))))
    }

    @Test
    fun `returns null when the open Chat was started by otherUserDid only`() {
        assertEquals(null, selectedConvoId(listOf(Chats, Chat(otherUserDid = "did:plc:alice"))))
    }
}
