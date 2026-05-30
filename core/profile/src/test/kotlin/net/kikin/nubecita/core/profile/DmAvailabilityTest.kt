package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ProfileAssociated
import io.github.kikin81.atproto.app.bsky.actor.ProfileAssociatedChat
import io.github.kikin81.atproto.app.bsky.actor.ViewerState
import io.github.kikin81.atproto.runtime.AtUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DmAvailabilityTest {
    private fun associatedWith(allowIncoming: String?): ProfileAssociated = ProfileAssociated(chat = allowIncoming?.let { ProfileAssociatedChat(allowIncoming = it) })

    private fun viewerFollowedBy(followed: Boolean): ViewerState = ViewerState(followedBy = if (followed) AtUri("at://did:plc:them/app.bsky.graph.follow/x") else null)

    @Test
    fun allowIncomingAll_isMessageable_evenWithoutFollowBack() {
        assertTrue(canViewerMessage(associatedWith("all"), viewerFollowedBy(false)))
    }

    @Test
    fun allowIncomingNone_isNotMessageable_evenWithFollowBack() {
        assertFalse(canViewerMessage(associatedWith("none"), viewerFollowedBy(true)))
    }

    @Test
    fun allowIncomingFollowing_withFollowBack_isMessageable() {
        assertTrue(canViewerMessage(associatedWith("following"), viewerFollowedBy(true)))
    }

    @Test
    fun allowIncomingFollowing_withoutFollowBack_isNotMessageable() {
        assertFalse(canViewerMessage(associatedWith("following"), viewerFollowedBy(false)))
    }

    // Absent `associated.chat` ⇒ Bluesky's default ("people you follow"), so it
    // behaves like "following": messageable only when the actor follows the viewer.
    @Test
    fun absentChat_withFollowBack_isMessageable() {
        assertTrue(canViewerMessage(associatedWith(allowIncoming = null), viewerFollowedBy(true)))
    }

    @Test
    fun absentChat_withoutFollowBack_isNotMessageable() {
        assertFalse(canViewerMessage(associatedWith(allowIncoming = null), viewerFollowedBy(false)))
    }

    @Test
    fun absentAssociated_withFollowBack_isMessageable() {
        assertTrue(canViewerMessage(associated = null, viewer = viewerFollowedBy(true)))
    }

    @Test
    fun absentAssociated_withoutViewer_isNotMessageable() {
        assertFalse(canViewerMessage(associated = null, viewer = null))
    }
}
