package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.convo.ConvoView
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewSender
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class ConvoMapperTest {
    @Test
    fun `picks the other member as the row's identity`() {
        val view =
            sampleConvoView(
                members =
                    listOf(
                        sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                        sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
                    ),
            )

        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)

        assertEquals("did:plc:alice", ui.otherUserDid)
        assertEquals("alice.bsky.social", ui.otherUserHandle)
        assertEquals("Alice", ui.displayName)
    }

    @Test
    fun `null displayName falls back to null and the row will display the handle upstream`() {
        val view = sampleConvoView(otherDisplayName = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        assertNull(ui.displayName)
    }

    @Test
    fun `blank displayName treated as null`() {
        val view = sampleConvoView(otherDisplayName = "   ")
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        assertNull(ui.displayName)
    }

    @Test
    fun `text MessageView populates the snippet and is not marked as attachment`() {
        val view = sampleConvoView(lastMessage = sampleMessage(text = "hey there", senderDid = "did:plc:alice"))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)

        assertEquals("hey there", ui.lastMessageSnippet)
        assertFalse(ui.lastMessageFromViewer)
        assertFalse(ui.lastMessageIsAttachment)
    }

    @Test
    fun `sender == viewer flips lastMessageFromViewer to true`() {
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = VIEWER_DID))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        assertTrue(ui.lastMessageFromViewer)
    }

    @Test
    fun `DeletedMessageView yields a 'Message deleted' snippet and isAttachment false`() {
        val view = sampleConvoView(lastMessage = sampleDeleted(senderDid = "did:plc:alice"))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)

        // Sentinel value the UI layer interprets via the chats_row_deleted_placeholder string resource.
        assertEquals("__deleted__", ui.lastMessageSnippet)
        assertFalse(ui.lastMessageIsAttachment)
    }

    @Test
    fun `null lastMessage yields null snippet`() {
        val view = sampleConvoView(lastMessage = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        assertNull(ui.lastMessageSnippet)
    }

    @Test
    fun `MessageView sentAt is propagated as a raw Instant`() {
        val sent = Instant.parse("2026-05-13T11:50:00Z")
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = "did:plc:alice", sentAt = sent))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        // Raw Instant: relative-time rendering lives in the UI layer via
        // rememberChatRelativeTimeText. Mapper just propagates the wire value.
        assertEquals(sent, ui.sentAt)
    }

    @Test
    fun `DeletedMessageView sentAt is propagated`() {
        val view = sampleConvoView(lastMessage = sampleDeleted(senderDid = "did:plc:alice"))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        // sampleDeleted hardcodes sentAt = 2026-05-13T17:50:00Z (see helper below).
        assertEquals(Instant.parse("2026-05-13T17:50:00Z"), ui.sentAt)
    }

    @Test
    fun `null lastMessage yields null sentAt`() {
        val view = sampleConvoView(lastMessage = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        assertNull(ui.sentAt)
    }

    @Test
    fun `unreadCount maps through (Long narrowed to Int)`() {
        val view = sampleConvoView(unreadCount = 7L)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID)
        assertEquals(7, ui.unreadCount)
    }

    @Test
    fun `zero unreadCount maps to 0`() {
        val ui = sampleConvoView(unreadCount = 0L).toConvoListItemUi(viewerDid = VIEWER_DID)
        assertEquals(0, ui.unreadCount)
    }

    @Test
    fun `muted flag maps through`() {
        assertTrue(sampleConvoView(muted = true).toConvoListItemUi(VIEWER_DID).muted)
        assertFalse(sampleConvoView(muted = false).toConvoListItemUi(VIEWER_DID).muted)
    }

    private companion object {
        const val VIEWER_DID = "did:plc:viewer123"
    }

    private fun sampleConvoView(
        id: String = "convo-id-1",
        members: List<ProfileViewBasic> =
            listOf(
                sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
            ),
        otherDid: String? = null,
        otherHandle: String? = null,
        otherDisplayName: String? = "Alice",
        lastMessage: io.github.kikin81.atproto.chat.bsky.convo.ConvoViewLastMessageUnion? = sampleMessage(),
        unreadCount: Long = 0L,
        muted: Boolean = false,
    ): ConvoView {
        val adjustedMembers =
            if (otherDid != null || otherHandle != null) {
                listOf(
                    sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                    sampleMember(
                        did = otherDid ?: "did:plc:alice",
                        handle = otherHandle ?: "alice.bsky.social",
                        displayName = otherDisplayName,
                    ),
                )
            } else {
                listOf(
                    members[0],
                    sampleMember(
                        did = (members[1].did).raw,
                        handle = (members[1].handle).raw,
                        displayName = otherDisplayName,
                    ),
                )
            }
        return ConvoView(
            id = id,
            members = adjustedMembers,
            muted = muted,
            rev = "0",
            unreadCount = unreadCount,
            lastMessage = lastMessage,
        )
    }

    private fun sampleMember(
        did: String,
        handle: String,
        displayName: String?,
    ): ProfileViewBasic =
        ProfileViewBasic(
            did = Did(did),
            handle = Handle(handle),
            displayName = displayName,
            avatar = null,
            associated = null,
            chatDisabled = null,
            createdAt = null,
            labels = null,
            verification = null,
            viewer = null,
        )

    private fun sampleMessage(
        text: String = "hello",
        senderDid: String = "did:plc:alice",
        sentAt: Instant = Instant.parse("2026-05-13T11:50:00Z"),
    ): MessageView =
        MessageView(
            id = "msg-id-1",
            rev = "0",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt.toString()),
            text = text,
            embed = null,
            facets = null,
            reactions = null,
        )

    private fun sampleDeleted(senderDid: String = "did:plc:alice"): DeletedMessageView =
        DeletedMessageView(
            id = "msg-id-deleted",
            rev = "0",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime("2026-05-13T17:50:00Z"),
        )
}
