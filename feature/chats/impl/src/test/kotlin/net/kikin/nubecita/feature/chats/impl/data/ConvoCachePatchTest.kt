package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
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
        unreadCount: Int = 0,
    ): ConvoRowUi =
        ConvoRowUi.Direct(
            convoId = id,
            otherUserDid = "did:plc:$id",
            otherUserHandle = "$id.bsky.social",
            displayName = id,
            avatarUrl = null,
            lastMessageSnippet = snippet,
            lastMessageFromViewer = false,
            lastMessageIsAttachment = false,
            sentAt = sentAt,
            unreadCount = unreadCount,
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
    fun `patchConvosPrepend hoists the convo to the front, de-duping by id`() {
        val before = persistentListOf(convo("alice"), convo("bob"))
        val after = patchConvosPrepend(before, convo("carol"))!!
        assertEquals(listOf("carol", "alice", "bob"), after.map { it.convoId })
    }

    @Test
    fun `patchConvosPrepend removes any existing row with the same id before prepending`() {
        val before = persistentListOf(convo("alice"), convo("bob"))
        val after = patchConvosPrepend(before, convo("bob", snippet = "fresh"))!!
        // bob appears once, at the front, with the new instance.
        assertEquals(listOf("bob", "alice"), after.map { it.convoId })
        assertEquals("fresh", after.first().lastMessageSnippet)
    }

    @Test
    fun `patchConvosPrepend returns null when the accepted cache is null`() {
        assertNull(patchConvosPrepend(null, convo("alice")))
    }

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

    @Test
    fun `patchConvosOnRead zeros the matching convo's unreadCount without reordering`() {
        val before =
            persistentListOf(
                convo("alice", unreadCount = 2),
                convo("bob", unreadCount = 5),
                convo("carol", unreadCount = 1),
            )

        val after = patchConvosOnRead(before, convoId = "bob")!!

        // Reading a convo must NOT hoist it — order is preserved.
        assertEquals(listOf("alice", "bob", "carol"), after.map { it.convoId })
        assertEquals(0, after[1].unreadCount)
        // Other rows untouched.
        assertEquals(2, after[0].unreadCount)
        assertEquals(1, after[2].unreadCount)
        // Last-message preview fields are left intact.
        assertEquals("old", after[1].lastMessageSnippet)
    }

    @Test
    fun `patchConvosOnRead returns null when the cache is null`() {
        assertNull(patchConvosOnRead(null, convoId = "anything"))
    }

    @Test
    fun `patchConvosOnRead is a no-op when the convo is not in the list`() {
        val before = persistentListOf(convo("alice", unreadCount = 3), convo("bob", unreadCount = 4))
        val after = patchConvosOnRead(before, convoId = "zzz")
        assertEquals(before, after)
    }

    @Test
    fun `patchConvosOnRead is idempotent on an already-read convo`() {
        val before = persistentListOf(convo("alice", unreadCount = 0), convo("bob", unreadCount = 2))
        val after = patchConvosOnRead(before, convoId = "alice")!!
        assertEquals(listOf("alice", "bob"), after.map { it.convoId })
        assertEquals(0, after[0].unreadCount)
        assertEquals(2, after[1].unreadCount)
    }
}
