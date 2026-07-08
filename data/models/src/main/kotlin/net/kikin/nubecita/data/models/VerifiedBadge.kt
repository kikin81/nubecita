package net.kikin.nubecita.data.models

/**
 * The verification badge to render for an account, derived from the atproto
 * `app.bsky.actor.defs#verificationState`.
 *
 * Exactly one badge applies at a time (mappers pick the highest tier):
 * [TrustedVerifier] outranks [Verified], and [None] renders nothing. Only a
 * `"valid"` status produces a badge — `"none"`, `"invalid"`, absent, or any
 * unrecognized status maps to [None].
 *
 * An enum is inherently `@Stable` for Compose, so no annotation is needed.
 */
public enum class VerifiedBadge {
    None,
    Verified,
    TrustedVerifier,
}
