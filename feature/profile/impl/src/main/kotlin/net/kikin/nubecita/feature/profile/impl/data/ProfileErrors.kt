package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.runtime.XrpcError

/**
 * Whether a `getProfile` failure means the profile is definitively gone —
 * deactivated, taken down, or not found — as opposed to a transient failure
 * (network, server error) where a cached copy should stay on screen.
 *
 * Error names follow the appview's `getProfile` contract: `AccountDeactivated`
 * and `AccountTakedown` are explicit; a missing profile surfaces as the
 * generic `InvalidRequest` with a "Profile not found" message.
 */
internal fun Throwable.isDefinitiveProfileGone(): Boolean {
    if (this !is XrpcError) return false
    return errorName in GONE_ERRORS ||
        (errorName == "InvalidRequest" && errorMessage?.contains("not found", ignoreCase = true) == true)
}

private val GONE_ERRORS = setOf("AccountDeactivated", "AccountTakedown")
