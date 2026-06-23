package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.group.GroupPublicView
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GroupPublicInfoMapperTest {
    private fun owner(
        handle: String = "alice.bsky.social",
        displayName: String? = "Alice",
        avatar: String? = "https://cdn/av.jpg",
    ) = ProfileViewBasic(
        did = Did("did:plc:alice"),
        handle = Handle(handle),
        displayName = displayName,
        avatar = avatar?.let { Uri(it) },
    )

    private fun view(
        name: String = "Book Club",
        memberCount: Long = 7,
        requireApproval: Boolean = true,
        owner: ProfileViewBasic = owner(),
    ) = GroupPublicView(
        memberCount = memberCount,
        name = name,
        owner = owner,
        requireApproval = requireApproval,
    )

    @Test
    fun `maps fields`() {
        val ui = view().toGroupPublicInfoUi()
        assertEquals("Book Club", ui.name)
        assertEquals(7, ui.memberCount)
        assertEquals("Alice", ui.ownerDisplayName)
        assertEquals("alice.bsky.social", ui.ownerHandle)
        assertEquals("https://cdn/av.jpg", ui.ownerAvatarUrl)
        assertEquals(true, ui.requireApproval)
    }

    @Test
    fun `blank display name maps to null`() {
        assertNull(view(owner = owner(displayName = "  ")).toGroupPublicInfoUi().ownerDisplayName)
    }

    @Test
    fun `null avatar maps to null`() {
        assertNull(view(owner = owner(avatar = null)).toGroupPublicInfoUi().ownerAvatarUrl)
    }
}
