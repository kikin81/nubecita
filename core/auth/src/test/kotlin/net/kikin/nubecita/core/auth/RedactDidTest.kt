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
        // Boundary: identifier length == DID_IDENTIFIER_PREVIEW (8). The
        // function should NOT append "…" when there's nothing to truncate.
        val did = "did:plc:abcdefgh"
        assertEquals(8, DID_IDENTIFIER_PREVIEW)
        assertEquals(did, did.redactDid())
    }

    @Test
    fun noColon_returnsInputUnchanged() {
        val raw = "abcdefghij"
        assertEquals(raw, raw.redactDid())
    }

    @Test
    fun trailingColon_returnsInputUnchanged() {
        val malformed = "did:plc:"
        assertEquals(malformed, malformed.redactDid())
    }
}
