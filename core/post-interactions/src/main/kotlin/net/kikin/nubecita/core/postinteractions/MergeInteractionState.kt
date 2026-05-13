package net.kikin.nubecita.core.postinteractions

import net.kikin.nubecita.core.postinteractions.internal.PENDING_LIKE_SENTINEL
import net.kikin.nubecita.core.postinteractions.internal.PENDING_REPOST_SENTINEL
import net.kikin.nubecita.data.models.PostUi

/**
 * Project a [PostInteractionState] from [PostInteractionsCache.state] onto
 * a [PostUi] instance loaded from wire data. Returns a new [PostUi] with
 * the viewer/stats fields updated to reflect the cache's truth.
 *
 * The cache's PENDING sentinels (see `internal/PendingSentinels.kt`) are
 * stripped on the way out — consumers see `null` in `viewer.likeUri` while
 * a like is in flight, but the boolean `viewer.isLikedByViewer` reads
 * `viewerLikeUri != null` so the heart still renders as liked.
 *
 * This extension lives in `:core/post-interactions` (NOT in `:data:models`)
 * to keep `:data:models` a pure leaf — the data-models module should be
 * ignorant of cache logic, sentinels, and projection rules. The
 * dependency direction is `:core/post-interactions` → `:data:models`,
 * matching the natural architectural arrow.
 *
 * # Usage
 *
 * ```kotlin
 * // Inside a VM subscriber:
 * cache.state.collect { interactionMap ->
 *     val merged = currentItems.map { item ->
 *         val state = interactionMap[item.post.id] ?: return@map item
 *         item.copy(post = item.post.mergeInteractionState(state))
 *     }
 *     setState { copy(items = merged.toImmutableList()) }
 * }
 * ```
 */
fun PostUi.mergeInteractionState(state: PostInteractionState): PostUi =
    copy(
        viewer =
            viewer.copy(
                isLikedByViewer = state.viewerLikeUri != null,
                likeUri = state.viewerLikeUri?.takeIf { it != PENDING_LIKE_SENTINEL },
                isRepostedByViewer = state.viewerRepostUri != null,
                repostUri = state.viewerRepostUri?.takeIf { it != PENDING_REPOST_SENTINEL },
            ),
        stats =
            stats.copy(
                likeCount = state.likeCount.toInt(),
                repostCount = state.repostCount.toInt(),
            ),
    )
