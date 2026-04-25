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
 */
@Stable
public data class AuthorUi(
    val did: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
)
