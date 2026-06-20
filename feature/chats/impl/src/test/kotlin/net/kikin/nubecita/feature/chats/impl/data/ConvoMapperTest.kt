package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.convo.ConvoView
import io.github.kikin81.atproto.chat.bsky.convo.ConvoViewKindUnion
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GroupConvo
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewSender
import io.github.kikin81.atproto.chat.bsky.group.JoinLinkView
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import net.kikin.nubecita.feature.chats.impl.ChatHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

    // --- toChatHeader / canViewerPost / toAuthorUi (group-chat Phase 1) ---

    @Test
    fun `toChatHeader on a GroupConvo yields a Group header with name and all members`() {
        val view =
            sampleConvoView(
                members =
                    listOf(
                        sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                        sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
                        sampleMember(did = "did:plc:bob", handle = "bob.bsky.social", displayName = "Bob"),
                    ),
                kind = sampleGroupConvo(name = "Weekend Crew"),
            )

        val header = view.toChatHeader(viewerDid = VIEWER_DID)

        val group = assertInstanceOf(ChatHeader.Group::class.java, header)
        assertEquals("Weekend Crew", group.name)
        // All members, including the viewer.
        assertEquals(3, group.members.size)
        val alice = group.members.first { it.did == "did:plc:alice" }
        assertEquals("alice.bsky.social", alice.handle)
        assertEquals("Alice", alice.displayName)
    }

    @Test
    fun `toChatHeader on a direct convo yields a Direct header for the other member`() {
        val view =
            sampleConvoView(
                members =
                    listOf(
                        sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                        sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
                    ),
            )

        val header = view.toChatHeader(viewerDid = VIEWER_DID)

        val direct = assertInstanceOf(ChatHeader.Direct::class.java, header)
        assertEquals("did:plc:alice", direct.did)
        assertEquals("alice.bsky.social", direct.handle)
        assertEquals("Alice", direct.displayName)
    }

    @Test
    fun `toAuthorUi falls back to the handle when displayName is null or blank, preserves a present one`() {
        assertEquals(
            "alice.bsky.social",
            sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = null).toAuthorUi().displayName,
        )
        assertEquals(
            "alice.bsky.social",
            sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "   ").toAuthorUi().displayName,
        )
        assertEquals(
            "Alice",
            sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice").toAuthorUi().displayName,
        )
    }

    @Test
    fun `canViewerPost gates on membership and group lock status`() {
        val members =
            listOf(
                sampleMember(did = VIEWER_DID, handle = "me.bsky.social", displayName = "Me"),
                sampleMember(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
            )

        // Member of an unlocked group → can post.
        assertTrue(
            sampleConvoView(members = members, kind = sampleGroupConvo(lockStatus = "unlocked")).canViewerPost(VIEWER_DID),
        )
        // Member of a locked group → cannot post.
        assertFalse(
            sampleConvoView(members = members, kind = sampleGroupConvo(lockStatus = "locked")).canViewerPost(VIEWER_DID),
        )
        // Member of a direct convo → can post.
        assertTrue(
            sampleConvoView(members = members).canViewerPost(VIEWER_DID),
        )
    }

    private companion object {
        const val VIEWER_DID = "did:plc:viewer123"
    }

    private fun sampleGroupConvo(
        name: String = "Group",
        lockStatus: String = "unlocked",
    ): GroupConvo =
        GroupConvo(
            joinLink =
                JoinLinkView(
                    code = "code-1",
                    createdAt = Datetime("2026-01-01T00:00:00Z"),
                    enabledStatus = "enabled",
                    joinRule = "open",
                    requireApproval = false,
                ),
            lockStatus = lockStatus,
            name = name,
        )

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
        kind: ConvoViewKindUnion? = null,
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
            } else if (members.size == 2) {
                listOf(
                    members[0],
                    sampleMember(
                        did = (members[1].did).raw,
                        handle = (members[1].handle).raw,
                        displayName = otherDisplayName,
                    ),
                )
            } else {
                // Group (or otherwise non-pair) member lists pass through untouched.
                members
            }
        return if (kind == null) {
            ConvoView(
                id = id,
                members = adjustedMembers,
                muted = muted,
                rev = "0",
                unreadCount = unreadCount,
                lastMessage = lastMessage,
            )
        } else {
            ConvoView(
                id = id,
                members = adjustedMembers,
                muted = muted,
                rev = "0",
                unreadCount = unreadCount,
                lastMessage = lastMessage,
                kind = kind,
            )
        }
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
