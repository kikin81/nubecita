package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant

/**
 * Fixture factories for [VerificationUi], mirroring the other `*Fixtures` objects in
 * this module so downstream preview/screenshot code (the verification sheet, profile
 * hero) renders deterministic data without a network round-trip.
 */
public object VerificationUiFixtures {
    private val DEFAULT_VERIFIED_AT: Instant = Instant.parse("2026-05-01T00:00:00Z")

    /** A verified account, verified by two organizations. */
    public fun verified(): VerificationUi =
        VerificationUi(
            badge = VerifiedBadge.Verified,
            verifiers =
                persistentListOf(
                    VerifierUi(
                        did = "did:plc:nytimes",
                        handle = "nytimes.com",
                        displayName = "The New York Times",
                        verifiedAt = DEFAULT_VERIFIED_AT,
                    ),
                    VerifierUi(
                        did = "did:plc:bsky",
                        handle = "bsky.app",
                        displayName = "Bluesky",
                        verifiedAt = DEFAULT_VERIFIED_AT,
                    ),
                ),
        )

    /** A trusted verifier (itself able to verify others). */
    public fun trustedVerifier(): VerificationUi =
        VerificationUi(
            badge = VerifiedBadge.TrustedVerifier,
            verifiers =
                persistentListOf(
                    VerifierUi(
                        did = "did:plc:bsky",
                        handle = "bsky.app",
                        displayName = "Bluesky",
                        verifiedAt = DEFAULT_VERIFIED_AT,
                    ),
                ),
        )
}
