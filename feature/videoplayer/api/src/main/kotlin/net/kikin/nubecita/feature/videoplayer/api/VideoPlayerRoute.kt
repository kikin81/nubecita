package net.kikin.nubecita.feature.videoplayer.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the fullscreen video player screen.
 *
 * Carries the AT URI of the post whose video should play (e.g.
 * `at://did:plc:.../app.bsky.feed.post/3kxyz`). Stored as a String, not
 * the lexicon-typed `AtUri` value class, so the NavKey serialization
 * format stays a single primitive field; the impl module's VM wraps to
 * `AtUri` at the XRPC boundary if it needs to look up the post via
 * `getPosts(uris)`. Mirrors the shape of
 * [net.kikin.nubecita.feature.postdetail.api.PostDetailRoute] —
 * single-primitive, deep-link-friendly.
 *
 * Lives in `:feature:videoplayer:api` so cross-feature modules
 * (feed / profile / postdetail) can push `VideoPlayerRoute` onto the
 * back stack without depending on `:feature:videoplayer:impl`. The
 * fullscreen Composable + ViewModel + chrome land in `:impl` under
 * `nubecita-zak.4`.
 *
 * Design context: `docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`.
 */
@Serializable
data class VideoPlayerRoute(
    val postUri: String,
) : NavKey
