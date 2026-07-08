package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Instant

/**
 * Full verification detail for a profile, powering the tap-to-explain sheet on the
 * profile header. The feed only needs [AuthorUi.verifiedBadge]; this richer type is
 * carried on the profile detail model where the sheet lists the issuing verifiers.
 *
 * [verifiers] contains only valid verifications (the mapper drops `isValid == false`),
 * already resolved to display names where possible.
 */
@Immutable
public data class VerificationUi(
    val badge: VerifiedBadge,
    val verifiers: ImmutableList<VerifierUi>,
)

/**
 * One account that issued a (valid) verification, resolved for display. [displayName]
 * is null when the issuer has none set — fall back to [handle]. [verifiedAt] is the
 * verification record's creation time.
 */
@Immutable
public data class VerifierUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val verifiedAt: Instant,
)
