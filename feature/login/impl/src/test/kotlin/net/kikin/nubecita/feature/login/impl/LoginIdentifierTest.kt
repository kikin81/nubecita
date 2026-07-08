package net.kikin.nubecita.feature.login.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LoginIdentifierTest {
    private fun assertNormalizes(vararg cases: Pair<String, String>) {
        cases.forEach { (input, expected) ->
            assertEquals(expected, normalizeLoginIdentifier(input), "normalize(\"$input\")")
        }
    }

    @Test
    fun `bare username is the only input suffixed with bsky_social`() {
        assertNormalizes(
            "alice" to "alice.bsky.social",
            "ALICE" to "alice.bsky.social",
        )
    }

    @Test
    fun `leading at-sign is stripped`() {
        assertNormalizes(
            "@alice" to "alice.bsky.social",
            "@alice.bsky.social" to "alice.bsky.social",
            "@franciscovelazquez.com" to "franciscovelazquez.com",
        )
    }

    @Test
    fun `already-canonical handle is unchanged and lowercased`() {
        assertNormalizes(
            "alice.bsky.social" to "alice.bsky.social",
            "Alice.Bsky.Social" to "alice.bsky.social",
        )
    }

    @Test
    fun `custom-domain handles are preserved, never suffixed with bsky_social`() {
        // The regression the user flagged: a dotted custom domain must pass through.
        assertNormalizes(
            "franciscovelazquez.com" to "franciscovelazquez.com",
            "FranciscoVelazquez.com" to "franciscovelazquez.com",
            "me.example.co.uk" to "me.example.co.uk",
        )
    }

    @Test
    fun `surrounding whitespace and inner at-padding are trimmed`() {
        assertNormalizes(
            "  alice  " to "alice.bsky.social",
            "  @Alice  " to "alice.bsky.social",
            "@ alice" to "alice.bsky.social",
            "  franciscovelazquez.com  " to "franciscovelazquez.com",
        )
    }

    @Test
    fun `DIDs pass through untouched — never lowercased-mangled or suffixed`() {
        assertNormalizes(
            "did:plc:abc123xyz" to "did:plc:abc123xyz",
            "did:web:example.com" to "did:web:example.com",
        )
    }

    @Test
    fun `blank or at-only input normalizes to empty, never a bare bsky_social`() {
        assertNormalizes(
            "" to "",
            "   " to "",
            "@" to "",
            "@   " to "",
        )
    }
}
