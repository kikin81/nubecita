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
 * created when they liked / reposted this post. They are non-null
 * exactly when the matching boolean is true, and the host VM passes them
 * back to `LikeRepostRepository.unlike(...)` / `unrepost(...)` to delete
 * the right record. Stored as raw strings (not `AtUri`) to keep this
 * UI-layer model atproto-runtime-light — the VM reconstructs the typed
 * value at the boundary.
 */
@Stable
public data class ViewerStateUi(
    val isLikedByViewer: Boolean = false,
    val isRepostedByViewer: Boolean = false,
    val isFollowingAuthor: Boolean = false,
    val likeUri: String? = null,
    val repostUri: String? = null,
)
