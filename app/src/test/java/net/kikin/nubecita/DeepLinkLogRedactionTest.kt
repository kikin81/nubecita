package net.kikin.nubecita

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for [redactDidsInPath] — the helper that gates which
 * substrings of a URI path are safe to land in `Timber.d` for the
 * unmatched deep-link diagnostic log.
 */
class DeepLinkLogRedactionTest {
    @Test
    fun didPlcSegmentIsRedacted() {
        assertEquals(
            "/profile/did:plc:abcdefgh…",
            redactDidsInPath("/profile/did:plc:abcdefghijklmnopqrstuvwx"),
        )
    }

    @Test
    fun didWebSegmentIsRedacted() {
        assertEquals(
            "/profile/did:web:example.…",
            redactDidsInPath("/profile/did:web:example.com"),
        )
    }

    @Test
    fun didWithColonsInMethodSpecificIdIsRedactedThroughTheFullIdentifier() {
        // did:web:example.com:user:alice — the redactDid helper anchors
        // on the first two colons, so the truncation applies to the
        // method-specific-id slice as a whole, not to the first
        // sub-segment.
        assertEquals(
            "/profile/did:web:example.…",
            redactDidsInPath("/profile/did:web:example.com:user:alice"),
        )
    }

    @Test
    fun handleIsNotRedacted() {
        // Per kf6k.5, handles are public AT Protocol identifiers — the
        // diagnostic value of seeing the handle in an unmatched-link log
        // outweighs the marginal privacy cost.
        assertEquals(
            "/profile/alice.bsky.social",
            redactDidsInPath("/profile/alice.bsky.social"),
        )
    }

    @Test
    fun rkeyIsNotRedacted() {
        // Same reasoning as handles — post rkeys are public identifiers
        // in the AT URI shape.
        assertEquals(
            "/profile/alice.bsky.social/post/3lkbabc",
            redactDidsInPath("/profile/alice.bsky.social/post/3lkbabc"),
        )
    }

    @Test
    fun didInRkeySlotIsRedactedTooButHandleNeighborIsNot() {
        // Defensive: even if a future path shape carries a DID outside
        // the {handle} slot, redaction still applies — the regex is
        // path-segment-anchored, not slot-anchored.
        assertEquals(
            "/profile/alice.bsky.social/post/did:plc:abcdefgh…",
            redactDidsInPath("/profile/alice.bsky.social/post/did:plc:abcdefghijklmnop"),
        )
    }

    @Test
    fun emptyPathIsUnchanged() {
        assertEquals("", redactDidsInPath(""))
    }

    @Test
    fun pathWithoutAnyDidIsUnchanged() {
        assertEquals("/oauth-redirect", redactDidsInPath("/oauth-redirect"))
    }
}
