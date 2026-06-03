package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit coverage for [selectedConvoDid] — the pure back-stack → open-convo
 * derivation that drives the list pane's selected-row highlight in the
 * tablet list-detail layout. Logic lives in a testable helper (not inlined
 * in the nav-module Composable) per the "assert logic in unit tests, not
 * screenshots" layering convention.
 */
internal class SelectedConvoDidTest {
    @Test
    fun `returns null when no Chat thread is on the back stack`() {
        assertNull(selectedConvoDid(listOf(Chats)))
    }

    @Test
    fun `returns null for an empty back stack`() {
        assertNull(selectedConvoDid(emptyList()))
    }

    @Test
    fun `returns the otherUserDid of the open Chat thread`() {
        val backStack: List<NavKey> = listOf(Chats, Chat(otherUserDid = "did:plc:alice"))
        assertEquals("did:plc:alice", selectedConvoDid(backStack))
    }

    @Test
    fun `returns the topmost Chat when several are stacked`() {
        val backStack: List<NavKey> =
            listOf(Chats, Chat("did:plc:alice"), Chat("did:plc:bob"))
        assertEquals("did:plc:bob", selectedConvoDid(backStack))
    }
}
