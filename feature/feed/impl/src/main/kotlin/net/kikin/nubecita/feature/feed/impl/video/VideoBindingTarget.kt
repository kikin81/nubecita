package net.kikin.nubecita.feature.feed.impl.video

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi

/**
 * What [FeedVideoPlayerCoordinator.bindMostVisibleVideo] needs to
 * configure the player: an identity (so the coordinator can answer
 * `boundPostId`) and the HLS playlist URL (so it can build the
 * `MediaItem`). A `null` binding target means "no video card meets the
 * visible-fraction threshold; coordinator should unbind and pause".
 */
internal data class VideoBindingTarget(
    val postId: String,
    val playlistUrl: String,
)

/**
 * Pure function: from the LazyColumn's [LazyListLayoutInfo] and the
 * post-id-keyed projection of currently-rendered posts, return the
 * topmost video card whose visible-fraction exceeds [VISIBILITY_THRESHOLD]
 * (0.6) — or `null` if none qualifies.
 *
 * Why a pure function (vs. inlined in the `LaunchedEffect` lambda):
 * the visibility math is the only piece of phase C that's worth a
 * unit test in isolation. Extracting it lets us assert "20 rapid
 * layoutInfo emissions during scroll → 0 binds" without standing up
 * a real `LazyListState`.
 *
 * Visibility math: an item is "visible" by however much of its
 * `[offset, offset + size)` interval intersects
 * `[viewportStartOffset, viewportEndOffset)`. Fraction = intersection
 * length / item size. Items entirely above or below the viewport
 * yield fraction 0.
 *
 * Topmost wins: `LazyListLayoutInfo.visibleItemsInfo` is sorted by
 * offset ascending, so the first item to satisfy the threshold is
 * also the topmost on screen — `firstOrNull` covers it. We don't
 * pick the "most visible" video because the spec ties binding to
 * scroll position, not engagement signal.
 */
internal fun mostVisibleVideoTarget(
    layoutInfo: LazyListLayoutInfo,
    postsById: Map<String, PostUi>,
): VideoBindingTarget? {
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    return layoutInfo.visibleItemsInfo
        .asSequence()
        .mapNotNull { item ->
            val key = item.key as? String ?: return@mapNotNull null
            val post = postsById[key] ?: return@mapNotNull null
            val embed = post.embed as? EmbedUi.Video ?: return@mapNotNull null
            val itemStart = item.offset
            val itemEnd = item.offset + item.size
            val visibleStart = itemStart.coerceAtLeast(viewportStart)
            val visibleEnd = itemEnd.coerceAtMost(viewportEnd)
            val visibleHeight = (visibleEnd - visibleStart).coerceAtLeast(0)
            val fraction =
                if (item.size > 0) visibleHeight.toFloat() / item.size.toFloat() else 0f
            if (fraction > VISIBILITY_THRESHOLD) {
                VideoBindingTarget(postId = post.id, playlistUrl = embed.playlistUrl)
            } else {
                null
            }
        }.firstOrNull()
}

/**
 * The visible-fraction threshold above which a video card qualifies
 * as the "most-visible video card" for the autoplay binding. 0.6
 * matches the openspec change `add-feature-feed-video-embeds`'s
 * design.md and is high enough that a card peeking in from the
 * top/bottom of the viewport doesn't claim the bind from a
 * mostly-on-screen sibling.
 */
private const val VISIBILITY_THRESHOLD: Float = 0.6f
