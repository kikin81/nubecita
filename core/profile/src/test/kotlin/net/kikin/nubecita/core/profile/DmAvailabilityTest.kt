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
    fun allowIncomingNone_isNotMessageable() {
        assertFalse(canViewerMessage(associatedWith("none"), viewerFollowedBy(true)))
    }

    @Test
    fun allowIncomingFollowing_withFollowedBy_isMessageable() {
        assertTrue(canViewerMessage(associatedWith("following"), viewerFollowedBy(true)))
    }

    @Test
    fun allowIncomingFollowing_withoutFollowedBy_isNotMessageable() {
        assertFalse(canViewerMessage(associatedWith("following"), viewerFollowedBy(false)))
    }

    @Test
    fun allowIncomingAll_isMessageable() {
        assertTrue(canViewerMessage(associatedWith("all"), viewerFollowedBy(false)))
    }

    @Test
    fun absentAssociated_isMessageable() {
        assertTrue(canViewerMessage(associated = null, viewer = null))
    }

    @Test
    fun associatedWithoutChat_isMessageable() {
        assertTrue(canViewerMessage(associatedWith(allowIncoming = null), viewer = null))
    }
}
