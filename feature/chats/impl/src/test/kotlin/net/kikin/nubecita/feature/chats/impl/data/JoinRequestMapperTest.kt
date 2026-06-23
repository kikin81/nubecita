package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.group.JoinRequestView
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class JoinRequestMapperTest {
    private fun profile(
        did: String = "did:plc:a",
        handle: String = "alice.bsky.social",
        displayName: String? = "Alice",
        avatar: String? = "https://cdn/a.jpg",
    ) = ProfileViewBasic(
        did = Did(did),
        handle = Handle(handle),
        displayName = displayName,
        avatar = avatar?.let { Uri(it) },
        associated = null,
        chatDisabled = null,
        createdAt = null,
        kind = null,
        labels = null,
        verification = null,
        viewer = null,
    )

    @Test
    fun `maps requestedBy fields and parses requestedAt`() {
        val view =
            JoinRequestView(
                convoId = "c1",
                requestedAt = Datetime("2026-06-22T10:00:00Z"),
                requestedBy = profile(),
            )
        val ui = view.toJoinRequestUi()
        assertEquals("did:plc:a", ui.did)
        assertEquals("alice.bsky.social", ui.handle)
        assertEquals("Alice", ui.displayName)
        assertEquals("https://cdn/a.jpg", ui.avatarUrl)
        assertEquals(Instant.parse("2026-06-22T10:00:00Z"), ui.requestedAt)
    }

    @Test
    fun `blank display name maps to null`() {
        val ui = JoinRequestView(convoId = "c1", requestedAt = Datetime("2026-06-22T10:00:00Z"), requestedBy = profile(displayName = "  ")).toJoinRequestUi()
        assertNull(ui.displayName)
    }
}
