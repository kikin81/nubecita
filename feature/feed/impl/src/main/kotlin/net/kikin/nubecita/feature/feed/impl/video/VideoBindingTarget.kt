package net.kikin.nubecita.feature.feed.impl.video

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi

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
            val target = videoBindingFor(post) ?: return@mapNotNull null
            val itemStart = item.offset
            val itemEnd = item.offset + item.size
            val visibleStart = itemStart.coerceAtLeast(viewportStart)
            val visibleEnd = itemEnd.coerceAtMost(viewportEnd)
            val visibleHeight = (visibleEnd - visibleStart).coerceAtLeast(0)
            val fraction =
                if (item.size > 0) visibleHeight.toFloat() / item.size.toFloat() else 0f
            if (fraction > VISIBILITY_THRESHOLD) target else null
        }.firstOrNull()
}

/**
 * Resolves a feed item to the [VideoBindingTarget] that should bind
 * the player when this item is the topmost visible video card.
 *
 * Precedence (B-lite per `add-feature-feed-record-embed`'s design):
 *
 * 1. **Parent video** — if `post.embed is EmbedUi.Video`, the bind
 *    identity is the parent post's id (`post.id`). Same shape as
 *    the pre-record-embed coordinator behavior.
 * 2. **Quoted-post video** — if the parent has no own video but
 *    `post.embed is EmbedUi.Record` whose `quotedPost.embed is
 *    QuotedEmbedUi.Video`, the bind identity is the quoted post's
 *    AT URI (`quotedPost.uri`). Naturally distinct from any parent
 *    bind key.
 * 3. **Neither** → `null`. The item carries no addressable video.
 *
 * The parent-video-wins rule covers the (vanishingly rare) case of
 * a video post that quotes another video post — the parent is the
 * post the user is "on" when scrolling reaches that item, so
 * binding to its own video matches user intent.
 *
 * Visibility math is unchanged — this function only resolves the
 * candidate target for an item; the caller still gates on the 0.6
 * visible-fraction threshold at the parent feed-item level. Sub-rect
 * geometry for quoted videos is explicitly out of scope.
 */
internal fun videoBindingFor(post: PostUi): VideoBindingTarget? {
    (post.embed as? EmbedUi.Video)?.let { video ->
        return VideoBindingTarget(postId = post.id, playlistUrl = video.playlistUrl)
    }
    (post.embed as? EmbedUi.Record)?.quotedPost?.let { quoted ->
        (quoted.embed as? QuotedEmbedUi.Video)?.let { video ->
            return VideoBindingTarget(postId = quoted.uri, playlistUrl = video.playlistUrl)
        }
    }
    return null
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
