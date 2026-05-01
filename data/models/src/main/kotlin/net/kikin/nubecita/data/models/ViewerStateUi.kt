package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * Current-viewer-specific flags for a [PostUi]. The viewer is the
 * authenticated user; these flags are stripped (or always `false`) for
 * unauthenticated rendering paths.
 *
 * Toggling like / repost in the UI fires a callback to the host VM, which
 * mutates server state and re-emits a new `PostUi` with an updated
 * `ViewerStateUi`. PostCard never flips these locally — see the PostCard
 * design doc, Decision 9 (loaded-state-only).
 *
 * [likeUri] / [repostUri] carry the AT URIs of the records the viewer
 * created when they liked / reposted this post. The host VM passes them
 * back to `LikeRepostRepository.unlike(...)` / `unrepost(...)` to delete
 * the right record. Stored as raw strings (not `AtUri`) to keep this
 * UI-layer model atproto-runtime-light — the VM reconstructs the typed
 * value at the boundary.
 *
 * `*Uri` may be null even when the matching boolean is true during the
 * optimistic-flip → server-response window: the VM flips
 * `isLikedByViewer` / `isRepostedByViewer` immediately on tap but only
 * learns the server-assigned record URI when `like(...)` / `repost(...)`
 * resolves. Callers that need to call unlike / unrepost MUST handle a
 * null URI as "no record to delete yet"; FeedViewModel rolls the
 * optimistic flip back when this happens so the UI doesn't drift into
 * a permanent isLiked-without-likeUri state.
 */
@Stable
public data class ViewerStateUi(
    val isLikedByViewer: Boolean = false,
    val isRepostedByViewer: Boolean = false,
    val isFollowingAuthor: Boolean = false,
    val likeUri: String? = null,
    val repostUri: String? = null,
)
