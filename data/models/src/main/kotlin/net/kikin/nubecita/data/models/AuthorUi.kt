package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * Display-ready snapshot of a Bluesky post author.
 *
 * Plain `String` fields — UI rendering doesn't need wire-level type safety
 * for `did` / `handle`. The mapper layer (in `:feature:*:impl`) is where
 * `io.github.kikin81.atproto.runtime.{Did, Handle}` typed wrappers exist; by
 * the time data reaches this UI model the wrappers have been unwrapped to
 * their `.raw` strings.
 *
 * `avatarUrl` is null when the author has no profile picture set; consumers
 * (typically `NubecitaAvatar`) handle the null case by falling back to the
 * placeholder painter.
 *
 * `verifiedBadge` is the account's verification tier derived from the wire
 * `verificationState`; `VerifiedBadge.None` (the default) renders no badge.
 *
 * `pronouns` is the profile's free-form pronouns text
 * (`app.bsky.actor.profile#pronouns`), trimmed, or null when unset or blank.
 * Only surfaces that show pronouns (the post-detail focus, profile hero,
 * composer reply parent) read it.
 *
 * `viewerFollowUri` is the signed-in viewer's `app.bsky.graph.follow` record
 * URI for this author (`viewer.following`), or null when the viewer doesn't
 * follow them. Unfollowing needs the URI, not just a boolean.
 */
@Stable
public data class AuthorUi(
    val did: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    val verifiedBadge: VerifiedBadge = VerifiedBadge.None,
    val pronouns: String? = null,
    val viewerFollowUri: String? = null,
)
