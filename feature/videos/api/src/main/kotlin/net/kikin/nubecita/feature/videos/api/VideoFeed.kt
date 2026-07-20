package net.kikin.nubecita.feature.videos.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the full-screen vertical video feed
 * (TikTok/reels-style).
 *
 * [startPostUri] is the AtUri of the post to open on — the Trending Videos
 * carousel pushes `VideoFeed(thumb.postUri)` so tapping a thumbnail opens the
 * swipeable feed at that video. `null` opens at the top.
 *
 * ## Why identity, not an index
 *
 * This key previously carried a `startIndex: Int`, which was wrong in two ways
 * and shipped a real bug (nubecita-zdv8.13):
 *
 * 1. The carousel and the feed each fetch the trending page **independently**,
 *    at different times. Trending is live, so the two responses need not agree
 *    on content or order — position N does not denote the same post on both
 *    sides.
 * 2. The two sides also filtered differently: the carousel indexed the raw page
 *    including non-video posts, while the feed compacted them away, so a single
 *    non-video post shifted every index after it.
 *
 * A URI survives both: the feed looks the post up in whatever page it actually
 * loaded, and falls back to the top when the post has aged out of it.
 *
 * Lives in `:feature:videos:api` so entry points can push it without depending
 * on `:feature:videos:impl`.
 */
@Serializable
data class VideoFeed(
    val startPostUri: String? = null,
) : NavKey
