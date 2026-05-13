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

        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)

        assertEquals("did:plc:alice", ui.otherUserDid)
        assertEquals("alice.bsky.social", ui.otherUserHandle)
        assertEquals("Alice", ui.displayName)
    }

    @Test
    fun `null displayName falls back to null and the row will display the handle upstream`() {
        val view = sampleConvoView(otherDisplayName = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertNull(ui.displayName)
    }

    @Test
    fun `blank displayName treated as null`() {
        val view = sampleConvoView(otherDisplayName = "   ")
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertNull(ui.displayName)
    }

    @Test
    fun `text MessageView populates the snippet and is not marked as attachment`() {
        val view = sampleConvoView(lastMessage = sampleMessage(text = "hey there", senderDid = "did:plc:alice"))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)

        assertEquals("hey there", ui.lastMessageSnippet)
        assertFalse(ui.lastMessageFromViewer)
        assertFalse(ui.lastMessageIsAttachment)
    }

    @Test
    fun `sender == viewer flips lastMessageFromViewer to true`() {
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = VIEWER_DID))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertTrue(ui.lastMessageFromViewer)
    }

    @Test
    fun `DeletedMessageView yields a 'Message deleted' snippet and isAttachment false`() {
        val view = sampleConvoView(lastMessage = sampleDeleted(senderDid = "did:plc:alice"))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)

        // Sentinel value the UI layer interprets via the chats_row_deleted_placeholder string resource.
        assertEquals("__deleted__", ui.lastMessageSnippet)
        assertFalse(ui.lastMessageIsAttachment)
    }

    @Test
    fun `null lastMessage yields null snippet`() {
        val view = sampleConvoView(lastMessage = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertNull(ui.lastMessageSnippet)
    }

    @Test
    fun `avatarHue is deterministic per (did, handle)`() {
        val a = sampleConvoView(otherDid = "did:plc:alice", otherHandle = "alice.bsky.social").toConvoListItemUi(VIEWER_DID, NOW)
        val b = sampleConvoView(otherDid = "did:plc:alice", otherHandle = "alice.bsky.social").toConvoListItemUi(VIEWER_DID, NOW)
        assertEquals(a.avatarHue, b.avatarHue)
        assertTrue(a.avatarHue in 0..359, "hue ${a.avatarHue} MUST be in [0, 359]")
    }

    @Test
    fun `messages sent in the last hour render relative minutes`() {
        val tenMinAgo = NOW.minus(kotlin.time.Duration.parse("10m"))
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = "did:plc:alice", sentAt = tenMinAgo))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("10m", ui.timestampRelative)
    }

    @Test
    fun `messages from earlier today render relative hours`() {
        val threeHoursAgo = NOW.minus(kotlin.time.Duration.parse("3h"))
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = "did:plc:alice", sentAt = threeHoursAgo))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("3h", ui.timestampRelative)
    }

    @Test
    fun `messages from yesterday render 'Yesterday'`() {
        // 28 hours ago — guaranteed yesterday in any locale where the previous calendar day exists.
        val yesterday = NOW.minus(kotlin.time.Duration.parse("28h"))
        val view = sampleConvoView(lastMessage = sampleMessage(text = "ok", senderDid = "did:plc:alice", sentAt = yesterday))
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("Yesterday", ui.timestampRelative)
    }

    @Test
    fun `null lastMessage yields empty timestamp`() {
        val view = sampleConvoView(lastMessage = null)
        val ui = view.toConvoListItemUi(viewerDid = VIEWER_DID, now = NOW)
        assertEquals("", ui.timestampRelative)
    }

    private companion object {
        const val VIEWER_DID = "did:plc:viewer123"
        val NOW: Instant = Instant.parse("2026-05-13T18:00:00Z")
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
            muted = false,
            rev = "0",
            unreadCount = 0L,
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
        sentAt: Instant = Instant.parse("2026-05-13T17:50:00Z"),
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
