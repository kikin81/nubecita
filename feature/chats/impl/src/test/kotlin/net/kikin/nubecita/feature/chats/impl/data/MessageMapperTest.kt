package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesResponseMessagesUnion
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewSender
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MessageMapperTest {
    private val viewer = "did:plc:viewer"
    private val peer = "did:plc:alice"

    @Test
    fun `MessageView from viewer maps to outgoing MessageUi`() {
        val wire = messageView(id = "m1", senderDid = viewer, text = "hi", sentAt = "2026-05-01T12:00:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(1, ui.size)
        assertEquals("m1", ui[0].id)
        assertTrue(ui[0].isOutgoing)
        assertEquals(false, ui[0].isDeleted)
        assertEquals("hi", ui[0].text)
    }

    @Test
    fun `MessageView from peer maps to incoming MessageUi`() {
        val wire = messageView(id = "m2", senderDid = peer, text = "yo", sentAt = "2026-05-01T12:01:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(false, ui[0].isOutgoing)
        assertEquals(peer, ui[0].senderDid)
    }

    @Test
    fun `DeletedMessageView maps to isDeleted MessageUi with empty text`() {
        val wire = deletedView(id = "m3", senderDid = peer, sentAt = "2026-05-01T12:02:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(1, ui.size)
        assertTrue(ui[0].isDeleted)
        assertEquals("", ui[0].text)
    }

    @Test
    fun `empty input yields empty output`() {
        val ui = listOf<GetMessagesResponseMessagesUnion>().toMessageUis(viewerDid = viewer)
        assertEquals(0, ui.size)
    }

    @Test
    fun `order is preserved`() {
        val a = messageView(id = "a", senderDid = viewer, text = "first", sentAt = "2026-05-01T12:00:00Z")
        val b = messageView(id = "b", senderDid = peer, text = "second", sentAt = "2026-05-01T12:01:00Z")
        val c = messageView(id = "c", senderDid = viewer, text = "third", sentAt = "2026-05-01T12:02:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(a, b, c).toMessageUis(viewerDid = viewer)
        assertEquals(listOf("a", "b", "c"), ui.map { it.id })
    }

    private fun messageView(
        id: String,
        senderDid: String,
        text: String,
        sentAt: String,
    ): MessageView =
        MessageView(
            embed = null,
            facets = null,
            id = id,
            reactions = null,
            rev = "rev-$id",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt),
            text = text,
        )

    private fun deletedView(
        id: String,
        senderDid: String,
        sentAt: String,
    ): DeletedMessageView =
        DeletedMessageView(
            id = id,
            rev = "rev-$id",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt),
        )
}
