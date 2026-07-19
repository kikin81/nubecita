package net.kikin.nubecita.feature.videos.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the full-screen vertical video feed
 * (TikTok/reels-style). [startIndex] is the item the surface opens on — the
 * Trending Videos carousel (a later slice of the vertical-video epic) pushes
 * this via `LocalMainShellNavState.current.add(VideoFeed(index))` so tapping a
 * thumbnail opens the swipeable feed at that video. Defaults to `0`.
 *
 * Lives in `:feature:videos:api` so entry points can push it without depending
 * on `:feature:videos:impl`.
 */
@Serializable
data class VideoFeed(
    val startIndex: Int = 0,
) : NavKey
