package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.embed.RecordView
import io.github.kikin81.atproto.app.bsky.embed.RecordViewBlocked
import io.github.kikin81.atproto.app.bsky.embed.RecordViewDetached
import io.github.kikin81.atproto.app.bsky.embed.RecordViewNotFound
import io.github.kikin81.atproto.app.bsky.embed.RecordViewRecord
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesResponseMessagesUnion
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewEmbedUnion
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewSender
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.kikin.nubecita.data.models.EmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `MessageView with no embed maps to MessageUi with null embed`() {
        val wire = messageView(id = "m", senderDid = peer, text = "no embed", sentAt = "2026-05-01T12:00:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertNull(ui[0].embed)
    }

    @Test
    fun `record embed with valid quoted post maps to EmbedUi Record`() {
        val embed = RecordView(record = quotedPostRecord(text = "quoted body"))
        val wire =
            messageView(
                id = "m",
                senderDid = peer,
                text = "look at this",
                sentAt = "2026-05-01T12:00:00Z",
                embed = embed,
            )
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        // Text and embed coexist on the same MessageUi.
        assertEquals("look at this", ui[0].text)
        val record = assertInstanceOf(EmbedUi.Record::class.java, ui[0].embed)
        assertEquals("quoted body", record.quotedPost.text)
        assertEquals("at://did:plc:quoted-author/app.bsky.feed.post/q", record.quotedPost.uri)
    }

    @Test
    fun `record embed with empty parent text still maps both text and embed`() {
        // The bd discovery case: a pure record-share has empty wire `text` —
        // the embed carries the share's content. Verify the mapper preserves
        // the empty `text` and populates `embed` so the UI layer can omit
        // the empty text bubble.
        val embed = RecordView(record = quotedPostRecord(text = "shared post body"))
        val wire =
            messageView(
                id = "m",
                senderDid = peer,
                text = "",
                sentAt = "2026-05-01T12:00:00Z",
                embed = embed,
            )
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals("", ui[0].text)
        assertInstanceOf(EmbedUi.Record::class.java, ui[0].embed)
    }

    @Test
    fun `record embed pointing at a not-found post maps to RecordUnavailable NotFound`() {
        val embed =
            RecordView(
                record = RecordViewNotFound(notFound = true, uri = AtUri("at://did:plc:gone/app.bsky.feed.post/x")),
            )
        val wire = messageView(id = "m", senderDid = peer, text = "", sentAt = "2026-05-01T12:00:00Z", embed = embed)
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
            ui[0].embed,
        )
    }

    @Test
    fun `record embed pointing at a blocked post maps to RecordUnavailable Blocked`() {
        val embed =
            RecordView(
                record =
                    RecordViewBlocked(
                        blocked = true,
                        author =
                            io.github.kikin81.atproto.app.bsky.feed.BlockedAuthor(
                                did = Did("did:plc:blocked"),
                            ),
                        uri = AtUri("at://did:plc:blocked/app.bsky.feed.post/x"),
                    ),
            )
        val wire = messageView(id = "m", senderDid = peer, text = "", sentAt = "2026-05-01T12:00:00Z", embed = embed)
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked),
            ui[0].embed,
        )
    }

    @Test
    fun `record embed pointing at a detached post maps to RecordUnavailable Detached`() {
        val embed =
            RecordView(
                record =
                    RecordViewDetached(
                        detached = true,
                        uri = AtUri("at://did:plc:author/app.bsky.feed.post/x"),
                    ),
            )
        val wire = messageView(id = "m", senderDid = peer, text = "", sentAt = "2026-05-01T12:00:00Z", embed = embed)
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(
            EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Detached),
            ui[0].embed,
        )
    }

    @Test
    fun `unknown embed union variant maps to null embed`() {
        // Forward-compat — any non-record embed union variant (today, only
        // `MessageViewEmbedUnion.Unknown`) drops to null; the sender's
        // intent isn't recoverable as a record-shape and rendering a
        // "Quoted post unavailable" chip would mis-state the situation.
        val unknown = MessageViewEmbedUnion.Unknown(type = "app.bsky.embed.future", raw = buildJsonObject {})
        val wire = messageView(id = "m", senderDid = peer, text = "hi", sentAt = "2026-05-01T12:00:00Z", embed = unknown)
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals("hi", ui[0].text)
        assertNull(ui[0].embed)
    }

    @Test
    fun `DeletedMessageView never carries an embed`() {
        val wire = deletedView(id = "m", senderDid = peer, sentAt = "2026-05-01T12:00:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertNull(ui[0].embed)
    }

    private fun messageView(
        id: String,
        senderDid: String,
        text: String,
        sentAt: String,
        embed: MessageViewEmbedUnion? = null,
    ): MessageView =
        MessageView(
            embed = embed,
            facets = null,
            id = id,
            reactions = null,
            rev = "rev-$id",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt),
            text = text,
        )

    /**
     * Builds a `RecordViewRecord` carrying a valid `app.bsky.feed.post`
     * payload so the mapper's JSON-decode path produces an
     * [EmbedUi.Record] (not a `RecordUnavailable.Unknown` fallback).
     */
    private fun quotedPostRecord(text: String): RecordViewRecord =
        RecordViewRecord(
            author =
                ProfileViewBasic(
                    did = Did("did:plc:quoted-author"),
                    handle = Handle("quoted.bsky.social"),
                    displayName = "Quoted Author",
                ),
            cid = Cid("bafyreifakequotedcid000000000000000000000000000"),
            indexedAt = Datetime("2026-05-01T12:00:00Z"),
            uri = AtUri("at://did:plc:quoted-author/app.bsky.feed.post/q"),
            value = appBskyFeedPostJson(text = text, createdAt = "2026-05-01T11:59:00Z"),
        )

    private fun appBskyFeedPostJson(
        text: String,
        createdAt: String,
    ): JsonObject =
        buildJsonObject {
            put("\$type", "app.bsky.feed.post")
            put("text", text)
            put("createdAt", createdAt)
        }

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
