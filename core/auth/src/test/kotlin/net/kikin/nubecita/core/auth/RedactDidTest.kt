package net.kikin.nubecita.core.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedactDidTest {
    @Test
    fun overLengthIdentifier_truncatesToPreviewWithEllipsis() {
        // did:plc:<24-char-base32> — bsky's normal shape.
        val did = "did:plc:abcdefghijklmnopqrstuvwx"
        assertEquals("did:plc:abcdefgh…", did.redactDid())
    }

    @Test
    fun underLengthIdentifier_returnsInputUnchanged() {
        val did = "did:plc:short"
        assertEquals(did, did.redactDid())
    }

    @Test
    fun identifierExactlyPreviewLength_returnsInputUnchanged() {
        // Boundary: identifier length == preview length. The function should
        // NOT append "…" when there's nothing to truncate.
        val did = "did:plc:abcdefgh"
        assertEquals(did, did.redactDid())
    }

    @Test
    fun noColon_returnsInputUnchanged() {
        val raw = "abcdefghij"
        assertEquals(raw, raw.redactDid())
    }

    @Test
    fun trailingColonAfterMethod_returnsInputUnchanged() {
        val malformed = "did:plc:"
        assertEquals(malformed, malformed.redactDid())
    }

    @Test
    fun singleColon_returnsInputUnchanged() {
        // No second colon → no parseable method-specific-id.
        val malformed = "did:plc"
        assertEquals(malformed, malformed.redactDid())
    }

    @Test
    fun multiSegmentMethodSpecificId_truncatesAcrossInternalColons() {
        // did:web identifiers carry path-style segments separated by ':'.
        // Anchoring on the LAST colon would leave the trailing segment as
        // the "identifier" and skip truncation when it's short — leaking
        // the full DID to the log surface. Anchor on the first two colons.
        val did = "did:web:example.com:user:alice"
        assertEquals("did:web:example.…", did.redactDid())
    }
}
