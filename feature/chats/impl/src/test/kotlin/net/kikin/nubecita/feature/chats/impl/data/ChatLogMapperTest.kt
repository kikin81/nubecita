package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GetLogResponse
import io.github.kikin81.atproto.chat.bsky.convo.GetLogResponseLogsUnion
import io.github.kikin81.atproto.chat.bsky.convo.LogCreateMessage
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewSender
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class ChatLogMapperTest {
    @Test
    fun `maps a create-message event with a text MessageView`() {
        val page =
            GetLogResponse(
                cursor = "next-1",
                logs = listOf(createMessage(convoId = "c1", message = messageView(id = "m1", senderDid = "did:plc:alice", text = "hi"))),
            ).toChatLogPage()

        assertEquals("next-1", page.nextCursor)
        assertEquals(1, page.events.size)
        val e = page.events.first()
        assertEquals("c1", e.convoId)
        assertEquals("m1", e.messageId)
        assertEquals("did:plc:alice", e.senderDid)
        assertEquals("hi", e.text)
        assertFalse(e.isDeleted)
        assertEquals(Instant.parse("2026-05-14T12:00:00Z"), e.sentAt)
    }

    @Test
    fun `maps a deleted create-message event with empty text and isDeleted true`() {
        val page =
            GetLogResponse(
                cursor = null,
                logs = listOf(createMessage(convoId = "c2", message = deletedMessageView(id = "m2", senderDid = "did:plc:bob"))),
            ).toChatLogPage()

        val e = page.events.single()
        assertEquals("c2", e.convoId)
        assertEquals("m2", e.messageId)
        assertEquals("did:plc:bob", e.senderDid)
        assertEquals("", e.text)
        assertTrue(e.isDeleted)
    }

    @Test
    fun `drops non-create log events but keeps the cursor`() {
        val page =
            GetLogResponse(
                cursor = "next-2",
                logs =
                    listOf(
                        GetLogResponseLogsUnion.Unknown(type = "chat.bsky.convo.defs#logReadMessage", raw = JsonObject(emptyMap())),
                        createMessage(convoId = "c3", message = messageView(id = "m3", senderDid = "did:plc:alice", text = "yo")),
                    ),
            ).toChatLogPage()

        assertEquals("next-2", page.nextCursor)
        assertEquals(listOf("m3"), page.events.map { it.messageId })
    }

    @Test
    fun `empty logs yield no events`() {
        val page = GetLogResponse(cursor = null, logs = emptyList()).toChatLogPage()
        assertTrue(page.events.isEmpty())
    }

    @Test
    fun `preserves order of create-message events`() {
        val page =
            GetLogResponse(
                cursor = null,
                logs =
                    listOf(
                        createMessage("c1", messageView("m1", "did:plc:alice", "first")),
                        createMessage("c1", messageView("m2", "did:plc:alice", "second")),
                    ),
            ).toChatLogPage()
        assertEquals(listOf("m1", "m2"), page.events.map { it.messageId })
    }

    private fun createMessage(
        convoId: String,
        message: io.github.kikin81.atproto.chat.bsky.convo.LogCreateMessageMessageUnion,
    ): LogCreateMessage = LogCreateMessage(convoId = convoId, message = message, rev = "0")

    private fun messageView(
        id: String,
        senderDid: String,
        text: String,
        sentAt: String = "2026-05-14T12:00:00Z",
    ): MessageView =
        MessageView(
            id = id,
            rev = "0",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt),
            text = text,
            embed = null,
            facets = null,
            reactions = null,
        )

    private fun deletedMessageView(
        id: String,
        senderDid: String,
        sentAt: String = "2026-05-14T12:00:00Z",
    ): DeletedMessageView =
        DeletedMessageView(
            id = id,
            rev = "0",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt),
        )
}
