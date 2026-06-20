package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ViewerState
import io.github.kikin81.atproto.chat.bsky.actor.DirectConvoMember
import io.github.kikin81.atproto.chat.bsky.actor.GroupConvoMember
import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasicKindUnion
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import net.kikin.nubecita.feature.chats.impl.FollowState
import net.kikin.nubecita.feature.chats.impl.GroupRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class GroupMemberMapperTest {
    @Test
    fun `owner role maps to GroupRole_Owner`() {
        val ui = sampleMember(kind = groupKind(role = "owner")).toGroupMemberUi(VIEWER_DID)
        assertEquals(GroupRole.Owner, ui.role)
    }

    @Test
    fun `standard role maps to GroupRole_Member`() {
        val ui = sampleMember(kind = groupKind(role = "standard")).toGroupMemberUi(VIEWER_DID)
        assertEquals(GroupRole.Member, ui.role)
    }

    @Test
    fun `null kind maps to GroupRole_Member`() {
        val ui = sampleMember(kind = null).toGroupMemberUi(VIEWER_DID)
        assertEquals(GroupRole.Member, ui.role)
    }

    @Test
    fun `DirectConvoMember kind maps to GroupRole_Member`() {
        val ui = sampleMember(kind = DirectConvoMember()).toGroupMemberUi(VIEWER_DID)
        assertEquals(GroupRole.Member, ui.role)
    }

    @Test
    fun `addedBy present yields its display name`() {
        val addedBy = sampleMember(did = "did:plc:owner", handle = "owner.bsky.social", displayName = "Owner")
        val ui = sampleMember(kind = groupKind(role = "standard", addedBy = addedBy)).toGroupMemberUi(VIEWER_DID)
        assertEquals("Owner", ui.addedByName)
    }

    @Test
    fun `addedBy with blank display name falls back to its handle`() {
        val addedBy = sampleMember(did = "did:plc:owner", handle = "owner.bsky.social", displayName = "   ")
        val ui = sampleMember(kind = groupKind(role = "standard", addedBy = addedBy)).toGroupMemberUi(VIEWER_DID)
        assertEquals("owner.bsky.social", ui.addedByName)
    }

    @Test
    fun `addedBy absent yields null addedByName (joined via link)`() {
        val ui = sampleMember(kind = groupKind(role = "standard", addedBy = null)).toGroupMemberUi(VIEWER_DID)
        assertNull(ui.addedByName)
    }

    @Test
    fun `viewer following set maps to Following with the follow uri`() {
        val followUri = "at://did:plc:alice/app.bsky.graph.follow/abc"
        val ui =
            sampleMember(viewer = ViewerState(following = AtUri(followUri)))
                .toGroupMemberUi(VIEWER_DID)
        assertEquals(FollowState.Following, ui.followState)
        assertEquals(followUri, ui.followUri)
    }

    @Test
    fun `viewer following null maps to NotFollowing with null uri`() {
        val ui = sampleMember(viewer = ViewerState(following = null)).toGroupMemberUi(VIEWER_DID)
        assertEquals(FollowState.NotFollowing, ui.followState)
        assertNull(ui.followUri)
    }

    @Test
    fun `null viewer maps to NotFollowing with null uri`() {
        val ui = sampleMember(viewer = null).toGroupMemberUi(VIEWER_DID)
        assertEquals(FollowState.NotFollowing, ui.followState)
        assertNull(ui.followUri)
    }

    @Test
    fun `self did sets isViewer true`() {
        val ui = sampleMember(did = VIEWER_DID, handle = "me.bsky.social").toGroupMemberUi(VIEWER_DID)
        assertTrue(ui.isViewer)
    }

    @Test
    fun `other did sets isViewer false`() {
        val ui = sampleMember(did = "did:plc:alice", handle = "alice.bsky.social").toGroupMemberUi(VIEWER_DID)
        assertFalse(ui.isViewer)
    }

    @Test
    fun `present display name is preserved`() {
        val ui = sampleMember(displayName = "Alice").toGroupMemberUi(VIEWER_DID)
        assertEquals("Alice", ui.displayName)
    }

    @Test
    fun `blank display name maps to null`() {
        val ui = sampleMember(displayName = "   ").toGroupMemberUi(VIEWER_DID)
        assertNull(ui.displayName)
    }

    @Test
    fun `null display name maps to null`() {
        val ui = sampleMember(displayName = null).toGroupMemberUi(VIEWER_DID)
        assertNull(ui.displayName)
    }

    @Test
    fun `did handle and avatar map through`() {
        val ui =
            sampleMember(
                did = "did:plc:alice",
                handle = "alice.bsky.social",
                avatar = "https://cdn.example/avatar.jpg",
            ).toGroupMemberUi(VIEWER_DID)
        assertEquals("did:plc:alice", ui.did)
        assertEquals("alice.bsky.social", ui.handle)
        assertEquals("https://cdn.example/avatar.jpg", ui.avatarUrl)
    }

    private companion object {
        const val VIEWER_DID = "did:plc:viewer123"
    }

    private fun groupKind(
        role: String,
        addedBy: ProfileViewBasic? = null,
    ): GroupConvoMember = GroupConvoMember(addedBy = addedBy, role = role)

    private fun sampleMember(
        did: String = "did:plc:alice",
        handle: String = "alice.bsky.social",
        displayName: String? = "Alice",
        avatar: String? = null,
        kind: ProfileViewBasicKindUnion? = null,
        viewer: ViewerState? = null,
    ): ProfileViewBasic =
        ProfileViewBasic(
            did = Did(did),
            handle = Handle(handle),
            displayName = displayName,
            avatar = avatar?.let { Uri(it) },
            associated = null,
            chatDisabled = null,
            createdAt = null,
            kind = kind,
            labels = null,
            verification = null,
            viewer = viewer,
        )
}
