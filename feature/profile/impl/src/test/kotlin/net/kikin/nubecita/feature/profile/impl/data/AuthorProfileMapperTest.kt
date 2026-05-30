package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileAssociated
import io.github.kikin81.atproto.app.bsky.actor.ProfileAssociatedChat
import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import io.github.kikin81.atproto.app.bsky.actor.ViewerState
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import net.kikin.nubecita.feature.profile.impl.ViewerModerationState
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [toProfileHeaderUi] — the inline atproto wire-to-UI
 * mapper for the profile header. Asserts the boundary contract: every
 * atproto runtime value class is unwrapped to a raw String at this
 * layer; nothing downstream sees `Did` / `Handle` / `Uri` / `Datetime`.
 */
internal class AuthorProfileMapperTest {
    @Test
    fun `unwraps value classes to raw strings`() {
        val view =
            sampleView(
                did = "did:plc:alice123",
                handle = "alice.bsky.social",
                avatar = "https://cdn.bsky.app/img/avatar/abc.jpg",
                banner = "https://cdn.bsky.app/img/banner/xyz.jpg",
                website = "https://alice.example.com",
            )

        val ui = view.toProfileHeaderUi()

        assertEquals("did:plc:alice123", ui.did)
        assertEquals("alice.bsky.social", ui.handle)
        assertEquals("https://cdn.bsky.app/img/avatar/abc.jpg", ui.avatarUrl)
        assertEquals("https://cdn.bsky.app/img/banner/xyz.jpg", ui.bannerUrl)
        assertEquals("https://alice.example.com", ui.website)
    }

    @Test
    fun `null-or-blank display name maps to null`() {
        val a = sampleView(displayName = null).toProfileHeaderUi()
        val b = sampleView(displayName = "").toProfileHeaderUi()
        val c = sampleView(displayName = "   ").toProfileHeaderUi()
        assertNull(a.displayName)
        assertNull(b.displayName)
        assertNull(c.displayName)
    }

    @Test
    fun `null-or-blank bio maps to null`() {
        assertNull(sampleView(description = null).toProfileHeaderUi().bio)
        assertNull(sampleView(description = "").toProfileHeaderUi().bio)
        assertNull(sampleView(description = "  \t  ").toProfileHeaderUi().bio)
    }

    @Test
    fun `null counts default to zero`() {
        val ui = sampleView(postsCount = null, followersCount = null, followsCount = null).toProfileHeaderUi()
        assertEquals(0L, ui.postsCount)
        assertEquals(0L, ui.followersCount)
        assertEquals(0L, ui.followsCount)
    }

    @Test
    fun `non-null counts pass through`() {
        val ui = sampleView(postsCount = 412L, followersCount = 2142L, followsCount = 342L).toProfileHeaderUi()
        assertEquals(412L, ui.postsCount)
        assertEquals(2142L, ui.followersCount)
        assertEquals(342L, ui.followsCount)
    }

    @Test
    fun `createdAt RFC3339 renders to Joined Month YYYY`() {
        val ui = sampleView(createdAt = "2023-04-15T12:34:56Z").toProfileHeaderUi()
        // Locale-dependent month name; assert prefix + year so the
        // test passes across locales without hard-coding "April".
        val joined = ui.joinedDisplay
        assertNotEquals(null, joined)
        assertEquals(true, joined!!.startsWith("Joined "), "expected 'Joined ' prefix, got: $joined")
        assertEquals(true, joined.contains("2023"), "expected year 2023 in: $joined")
    }

    @Test
    fun `unparseable createdAt yields null joinedDisplay`() {
        val ui = sampleView(createdAt = "not-a-date").toProfileHeaderUi()
        assertNull(ui.joinedDisplay)
    }

    @Test
    fun `avatarHue is deterministic across calls`() {
        val a = sampleView(did = "did:plc:alice123", handle = "alice.bsky.social").toProfileHeaderUi()
        val b = sampleView(did = "did:plc:alice123", handle = "alice.bsky.social").toProfileHeaderUi()
        assertEquals(a.avatarHue, b.avatarHue)
    }

    @Test
    fun `avatarHue varies across distinct users`() {
        val alice = sampleView(did = "did:plc:alice123", handle = "alice.bsky.social").toProfileHeaderUi()
        val bob = sampleView(did = "did:plc:bob456", handle = "bob.bsky.social").toProfileHeaderUi()
        // Probabilistically these should differ; assert that they do
        // for our specific test inputs (computed manually + locked).
        assertNotEquals(alice.avatarHue, bob.avatarHue)
    }

    @Test
    fun `avatarHue is always in 0 to 360 range`() {
        listOf("did:plc:a", "did:plc:b", "did:plc:c", "did:plc:zzzzzzzzzzzz").forEach { d ->
            val hue = sampleView(did = d, handle = "user.bsky.social").toProfileHeaderUi().avatarHue
            assertEquals(true, hue in 0..359, "hue $hue MUST be in [0, 359] for did=$d")
        }
    }

    @Test
    fun `toProfileHeaderWithViewer returns ViewerRelationship_None when viewer is null`() {
        val wire = sampleView(viewer = null)

        val result = wire.toProfileHeaderWithViewer()

        assertEquals(ViewerRelationship.None, result.viewerRelationship)
    }

    @Test
    fun `toProfileHeaderWithViewer returns ViewerRelationship_Following carrying the follow AT-URI when viewer follows`() {
        val followUri = "at://did:plc:viewer/app.bsky.graph.follow/abc"
        val wire =
            sampleView(
                viewer = ViewerState(following = AtUri(followUri)),
            )

        val result = wire.toProfileHeaderWithViewer()

        assertEquals(
            ViewerRelationship.Following(followUri = followUri, isPending = false),
            result.viewerRelationship,
        )
    }

    @Test
    fun `toProfileHeaderWithViewer returns ViewerRelationship_NotFollowing when viewer does not follow the subject`() {
        val wire =
            sampleView(
                viewer = ViewerState(following = null),
            )

        val result = wire.toProfileHeaderWithViewer()

        assertEquals(ViewerRelationship.NotFollowing(isPending = false), result.viewerRelationship)
    }

    @Test
    fun `canMessage is false when associated chat is absent and the actor does not follow the viewer`() {
        // Absent chat declaration ⇒ Bluesky default "people you follow"; with no
        // follow-back the viewer cannot DM them (matches the official client).
        val ui = sampleView(associated = null, viewer = ViewerState(followedBy = null)).toProfileHeaderUi()
        assertEquals(false, ui.canMessage)
    }

    @Test
    fun `canMessage is true when associated chat is absent but the actor follows the viewer`() {
        val ui =
            sampleView(
                associated = null,
                viewer = ViewerState(followedBy = AtUri("at://did:plc:other/app.bsky.graph.follow/abc")),
            ).toProfileHeaderUi()
        assertEquals(true, ui.canMessage)
    }

    @Test
    fun `canMessage is true when allowIncoming is all`() {
        val ui =
            sampleView(
                associated = ProfileAssociated(chat = ProfileAssociatedChat(allowIncoming = "all")),
            ).toProfileHeaderUi()
        assertEquals(true, ui.canMessage)
    }

    @Test
    fun `canMessage is false when allowIncoming is none, regardless of followedBy`() {
        val ui =
            sampleView(
                associated = ProfileAssociated(chat = ProfileAssociatedChat(allowIncoming = "none")),
                viewer = ViewerState(followedBy = AtUri("at://did:plc:other/app.bsky.graph.follow/abc")),
            ).toProfileHeaderUi()
        assertEquals(false, ui.canMessage)
    }

    @Test
    fun `canMessage is true when allowIncoming is following and the actor follows the viewer`() {
        val ui =
            sampleView(
                associated = ProfileAssociated(chat = ProfileAssociatedChat(allowIncoming = "following")),
                viewer = ViewerState(followedBy = AtUri("at://did:plc:other/app.bsky.graph.follow/abc")),
            ).toProfileHeaderUi()
        assertEquals(true, ui.canMessage)
    }

    @Test
    fun `canMessage is false when allowIncoming is following but the actor does not follow the viewer`() {
        val ui =
            sampleView(
                associated = ProfileAssociated(chat = ProfileAssociatedChat(allowIncoming = "following")),
                viewer = ViewerState(followedBy = null),
            ).toProfileHeaderUi()
        assertEquals(false, ui.canMessage)
    }

    @Test
    fun `viewerModeration defaults to all-false when viewer is null`() {
        val ui = sampleView(viewer = null).toProfileHeaderUi()
        assertEquals(ViewerModerationState(), ui.viewerModeration)
    }

    @Test
    fun `viewerModeration captures muted-only viewer state`() {
        val ui =
            sampleView(viewer = ViewerState(muted = true)).toProfileHeaderUi()
        assertEquals(
            ViewerModerationState(
                isMutedByViewer = true,
                blockUri = null,
                isBlockingViewer = false,
            ),
            ui.viewerModeration,
        )
    }

    @Test
    fun `viewerModeration captures blocking-only viewer state and exposes the block AT URI`() {
        val blockUri = "at://did:plc:viewer/app.bsky.graph.block/abc"
        val ui =
            sampleView(viewer = ViewerState(blocking = AtUri(blockUri))).toProfileHeaderUi()
        assertEquals(
            ViewerModerationState(
                isMutedByViewer = false,
                blockUri = blockUri,
                isBlockingViewer = false,
            ),
            ui.viewerModeration,
        )
    }

    @Test
    fun `viewerModeration captures blockedBy-only viewer state`() {
        val ui =
            sampleView(viewer = ViewerState(blockedBy = true)).toProfileHeaderUi()
        assertEquals(
            ViewerModerationState(
                isMutedByViewer = false,
                blockUri = null,
                isBlockingViewer = true,
            ),
            ui.viewerModeration,
        )
    }

    @Test
    fun `viewerModeration captures both directions when viewer is blocking and is blocked by`() {
        val blockUri = "at://did:plc:viewer/app.bsky.graph.block/def"
        val ui =
            sampleView(
                viewer =
                    ViewerState(
                        blocking = AtUri(blockUri),
                        blockedBy = true,
                    ),
            ).toProfileHeaderUi()
        assertEquals(
            ViewerModerationState(
                isMutedByViewer = false,
                blockUri = blockUri,
                isBlockingViewer = true,
            ),
            ui.viewerModeration,
        )
    }

    @Test
    fun `viewerModeration is all-false when viewer is present but no moderation flags are set`() {
        val ui =
            sampleView(viewer = ViewerState(following = null)).toProfileHeaderUi()
        assertEquals(ViewerModerationState(), ui.viewerModeration)
    }

    @Test
    fun `canMessage treats an unrecognized allowIncoming value like following`() {
        // Anything we don't recognize (a future appview token or a typo) is gated
        // like the "following" default: messageable only with a follow-back, never
        // fail-open — an unknown setting must not surface an un-DM-able recipient.
        val notFollowed =
            sampleView(
                associated = ProfileAssociated(chat = ProfileAssociatedChat(allowIncoming = "mutuals")),
                viewer = ViewerState(followedBy = null),
            ).toProfileHeaderUi()
        assertEquals(false, notFollowed.canMessage)

        val followed =
            sampleView(
                associated = ProfileAssociated(chat = ProfileAssociatedChat(allowIncoming = "mutuals")),
                viewer = ViewerState(followedBy = AtUri("at://did:plc:other/app.bsky.graph.follow/abc")),
            ).toProfileHeaderUi()
        assertEquals(true, followed.canMessage)
    }

    private fun sampleView(
        did: String = "did:plc:alice123",
        handle: String = "alice.bsky.social",
        displayName: String? = "Alice",
        avatar: String? = null,
        banner: String? = null,
        website: String? = null,
        description: String? = null,
        createdAt: String? = null,
        postsCount: Long? = null,
        followersCount: Long? = null,
        followsCount: Long? = null,
        viewer: ViewerState? = null,
        associated: ProfileAssociated? = null,
    ): ProfileViewDetailed =
        ProfileViewDetailed(
            did = Did(did),
            handle = Handle(handle),
            displayName = displayName,
            avatar = avatar?.let(::Uri),
            banner = banner?.let(::Uri),
            website = website?.let(::Uri),
            description = description,
            createdAt = createdAt?.let(::Datetime),
            postsCount = postsCount,
            followersCount = followersCount,
            followsCount = followsCount,
            viewer = viewer,
            associated = associated,
        )
}
