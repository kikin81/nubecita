package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
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
        )
}
