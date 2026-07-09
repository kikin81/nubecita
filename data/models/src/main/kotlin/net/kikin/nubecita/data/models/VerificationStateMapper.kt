package net.kikin.nubecita.data.models

import io.github.kikin81.atproto.app.bsky.actor.VerificationState

/**
 * Derive the display [VerifiedBadge] from the wire `verificationState`. Trusted
 * verifier outranks verified; only a `"valid"` status shows a badge — `"none"`,
 * `"invalid"`, absent, or any unrecognized value maps to [VerifiedBadge.None].
 *
 * Shared by every wire→UI actor mapper (feed/profile authors in
 * `:core:feed-mapping`, search/typeahead actors in `:core:actors`) so the
 * precedence + `"valid"`-only rules stay in one place. `VerificationState` is an
 * `atproto:models` value type, allowed here as a mapper input per the
 * `:data:models` conventions (same latitude as `Facet`/`Handle` field types).
 */
public fun VerificationState?.toVerifiedBadge(): VerifiedBadge =
    when {
        this == null -> VerifiedBadge.None
        trustedVerifierStatus == "valid" -> VerifiedBadge.TrustedVerifier
        verifiedStatus == "valid" -> VerifiedBadge.Verified
        else -> VerifiedBadge.None
    }
