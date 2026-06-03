package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class ConvoCachePatchTest {
    private fun convo(
        id: String,
        snippet: String? = "old",
        sentAt: Instant? = Instant.parse("2026-05-01T10:00:00Z"),
    ): ConvoListItemUi =
        ConvoListItemUi(
            convoId = id,
            otherUserDid = "did:plc:$id",
            otherUserHandle = "$id.bsky.social",
            displayName = id,
            avatarUrl = null,
            avatarHue = 0,
            lastMessageSnippet = snippet,
            lastMessageFromViewer = false,
            lastMessageIsAttachment = false,
            sentAt = sentAt,
        )

    private fun sent(
        text: String,
        at: Instant = Instant.parse("2026-05-01T12:00:00Z"),
    ): MessageUi =
        MessageUi(
            id = "m1",
            senderDid = "did:plc:viewer",
            isOutgoing = true,
            text = text,
            isDeleted = false,
            sentAt = at,
        )

    @Test
    fun `patches the matching convo and moves it to the top`() {
        val now = Instant.parse("2026-05-01T12:30:00Z")
        val before = persistentListOf(convo("alice"), convo("bob"), convo("carol"))

        val after = patchConvosOnSend(before, convoId = "bob", message = sent("hey there", now))!!

        // bob hoisted to index 0.
        assertEquals(listOf("bob", "alice", "carol"), after.map { it.convoId })
        val bob = after.first()
        assertEquals("hey there", bob.lastMessageSnippet)
        assertTrue(bob.lastMessageFromViewer)
        assertEquals(false, bob.lastMessageIsAttachment)
        assertEquals(now, bob.sentAt)
        // Other rows untouched.
        assertEquals("old", after[1].lastMessageSnippet)
    }

    @Test
    fun `returns null when the cache is null (inbox not loaded)`() {
        assertNull(patchConvosOnSend(null, convoId = "anything", message = sent("hi")))
    }

    @Test
    fun `is a no-op when the convo is not in the list`() {
        val before = persistentListOf(convo("alice"), convo("bob"))
        val after = patchConvosOnSend(before, convoId = "zzz", message = sent("hi"))
        assertEquals(before, after)
    }

    @Test
    fun `patches a convo that is already at the top`() {
        val before = persistentListOf(convo("alice"), convo("bob"))
        val after = patchConvosOnSend(before, convoId = "alice", message = sent("first"))!!
        assertEquals(listOf("alice", "bob"), after.map { it.convoId })
        assertEquals("first", after.first().lastMessageSnippet)
        assertTrue(after.first().lastMessageFromViewer)
    }
}
