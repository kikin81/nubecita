package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class DmNotificationLogicTest {
    // ---- 3.1 toDmNotifyPlan ----

    @Test
    fun `notifies an inbound message in an unread convo and advances the cursor`() {
        val page =
            ChatLogPage(
                events = listOf(event(convoId = "c1", senderDid = ALICE, messageId = "m1")).toImmutableList(),
                nextCursor = "next-1",
            )

        val plan = page.toDmNotifyPlan(viewerDid = VIEWER, unreadConvoIds = setOf("c1"))

        assertEquals(listOf("m1"), plan.toNotify.map { it.messageId })
        assertEquals("next-1", plan.advancedCursor)
    }

    @Test
    fun `excludes the viewer's own outgoing messages`() {
        val page =
            ChatLogPage(
                events =
                    listOf(
                        event(convoId = "c1", senderDid = VIEWER, messageId = "out"),
                        event(convoId = "c1", senderDid = ALICE, messageId = "in"),
                    ).toImmutableList(),
            )

        val plan = page.toDmNotifyPlan(viewerDid = VIEWER, unreadConvoIds = setOf("c1"))

        assertEquals(listOf("in"), plan.toNotify.map { it.messageId })
    }

    @Test
    fun `excludes messages whose convo is no longer unread (read-state filter)`() {
        val page =
            ChatLogPage(
                events =
                    listOf(
                        event(convoId = "read", senderDid = ALICE, messageId = "r"),
                        event(convoId = "unread", senderDid = ALICE, messageId = "u"),
                    ).toImmutableList(),
            )

        val plan = page.toDmNotifyPlan(viewerDid = VIEWER, unreadConvoIds = setOf("unread"))

        assertEquals(listOf("u"), plan.toNotify.map { it.messageId })
    }

    @Test
    fun `caps the number of notifications per run`() {
        val many = (1..MAX_DM_NOTIFICATIONS_PER_RUN + 10).map { event("c$it", ALICE, "m$it") }
        val unread = many.map { it.convoId }.toSet()
        val page = ChatLogPage(events = many.toImmutableList(), nextCursor = "c")

        val plan = page.toDmNotifyPlan(viewerDid = VIEWER, unreadConvoIds = unread)

        assertEquals(MAX_DM_NOTIFICATIONS_PER_RUN, plan.toNotify.size)
        // The cursor still advances past the whole page so the backlog isn't re-processed.
        assertEquals("c", plan.advancedCursor)
    }

    @Test
    fun `empty page yields an empty plan but carries the cursor`() {
        val plan = ChatLogPage(nextCursor = "head").toDmNotifyPlan(viewerDid = VIEWER, unreadConvoIds = emptySet())
        assertTrue(plan.toNotify.isEmpty())
        assertEquals("head", plan.advancedCursor)
    }

    // ---- 3.2 toDmNotificationContent ----

    @Test
    fun `content uses the sender display name and the message text`() {
        val content =
            event(convoId = "c1", senderDid = ALICE, messageId = "m1", text = "hey there")
                .toDmNotificationContent(senderDisplayName = "Alice", senderHandle = "alice.bsky.social")

        assertEquals("Alice", content.title)
        assertEquals("hey there", content.body)
    }

    @Test
    fun `content falls back to the handle when display name is null or blank`() {
        val e = event(convoId = "c1", senderDid = ALICE, messageId = "m1", text = "hi")
        assertEquals("alice.bsky.social", e.toDmNotificationContent(null, "alice.bsky.social").title)
        assertEquals("alice.bsky.social", e.toDmNotificationContent("   ", "alice.bsky.social").title)
    }

    @Test
    fun `deleted message maps to the v1 deleted sentinel body`() {
        val content =
            event(convoId = "c1", senderDid = ALICE, messageId = "m1", text = "", isDeleted = true)
                .toDmNotificationContent(senderDisplayName = "Alice", senderHandle = "alice.bsky.social")

        assertEquals(DELETED_MESSAGE_SNIPPET, content.body)
    }

    private companion object {
        const val VIEWER = "did:plc:viewer"
        const val ALICE = "did:plc:alice"
    }

    private fun event(
        convoId: String,
        senderDid: String,
        messageId: String,
        text: String = "hello",
        isDeleted: Boolean = false,
    ): ChatLogEvent =
        ChatLogEvent(
            convoId = convoId,
            messageId = messageId,
            senderDid = senderDid,
            text = text,
            isDeleted = isDeleted,
            sentAt = Instant.parse("2026-05-14T12:00:00Z"),
        )
}
