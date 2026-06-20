package net.kikin.nubecita.feature.chats.impl

import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit coverage for [selectedConvoId] / [selectedOtherUserDid] — the pure
 * back-stack → open-convo derivations that drive the list pane's selected-row
 * highlight in the tablet list-detail layout. Logic lives in testable helpers
 * (not inlined in the nav-module Composable) per the "assert logic in unit
 * tests, not screenshots" layering convention.
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

    @Test
    fun `selectedOtherUserDid returns the did of a profile-initiated Chat`() {
        assertEquals("did:plc:alice", selectedOtherUserDid(listOf(Chats, Chat(otherUserDid = "did:plc:alice"))))
    }

    @Test
    fun `selectedOtherUserDid is null when the open Chat was opened by convoId`() {
        assertNull(selectedOtherUserDid(listOf(Chats, Chat(convoId = "convo-1"))))
    }

    @Test
    fun `selectedOtherUserDid is null when no Chat thread is on the back stack`() {
        assertNull(selectedOtherUserDid(listOf(Chats)))
    }
}
