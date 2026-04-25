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
 */
@Stable
public data class ViewerStateUi(
    val isLikedByViewer: Boolean = false,
    val isRepostedByViewer: Boolean = false,
    val isFollowingAuthor: Boolean = false,
)
