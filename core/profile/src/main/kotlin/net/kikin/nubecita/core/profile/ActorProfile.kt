package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed

/**
 * Slim, surface-agnostic projection of an `app.bsky.actor.getProfile`
 * response — just the universally-useful fields. Each consumer
 * (Settings identity header, future hover cards, multi-account
 * pickers) reads only what it needs.
 *
 * Surfaces that need a richer view (banner, bio, stats, viewer
 * relationship — Profile hero) keep their own UI-model projections
 * in their own modules and call the underlying XRPC directly; the
 * full wire model has fields that aren't broadly reusable.
 *
 * [displayName] is unwrapped to `null` when the wire field is absent
 * or blank — callers can treat null as "no name" without a second
 * `isBlank()` check.
 */
data class ActorProfile(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)

/**
 * Maps the atproto wire model into [ActorProfile]. Exposed (rather
 * than inlined inside the repository) so tests can pin the projection
 * shape directly without booting an `XrpcClient`.
 */
internal fun ProfileViewDetailed.toActorProfile(): ActorProfile =
    ActorProfile(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeUnless { it.isBlank() },
        avatarUrl = avatar?.raw,
    )
