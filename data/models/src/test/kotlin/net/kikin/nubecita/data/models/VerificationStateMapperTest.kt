package net.kikin.nubecita.data.models

import io.github.kikin81.atproto.app.bsky.actor.VerificationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VerificationStateMapperTest {
    // The status fields are non-null String in the SDK (default "none" = not verified);
    // only the enclosing `verification` object is nullable.
    private fun state(
        verified: String = "none",
        trusted: String = "none",
    ) = VerificationState(
        verifiedStatus = verified,
        trustedVerifierStatus = trusted,
        verifications = emptyList(),
    )

    @Test
    fun `null verification maps to None`() {
        assertEquals(VerifiedBadge.None, (null as VerificationState?).toVerifiedBadge())
    }

    @Test
    fun `verifiedStatus valid maps to Verified`() {
        assertEquals(VerifiedBadge.Verified, state(verified = "valid").toVerifiedBadge())
    }

    @Test
    fun `trustedVerifierStatus valid maps to TrustedVerifier`() {
        assertEquals(VerifiedBadge.TrustedVerifier, state(trusted = "valid").toVerifiedBadge())
    }

    @Test
    fun `trusted verifier outranks verified`() {
        assertEquals(
            VerifiedBadge.TrustedVerifier,
            state(verified = "valid", trusted = "valid").toVerifiedBadge(),
        )
    }

    @Test
    fun `none, invalid, or unknown status maps to None`() {
        assertEquals(VerifiedBadge.None, state(verified = "none", trusted = "none").toVerifiedBadge())
        assertEquals(VerifiedBadge.None, state(verified = "invalid").toVerifiedBadge())
        // Comparison is exact + case-sensitive, so a future/unknown value degrades safely.
        assertEquals(VerifiedBadge.None, state(verified = "VALID").toVerifiedBadge())
        assertEquals(VerifiedBadge.None, state().toVerifiedBadge())
    }
}
