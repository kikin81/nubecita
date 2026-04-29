package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.FeedItemUi

/**
 * `app.bsky.feed.getTimeline` fetch surface scoped to `:feature:feed:impl`.
 *
 * The interface is package-internal: no other module imports it. If a
 * second consumer (post detail, search) later needs the same fetch, the
 * change that adds the consumer also promotes this interface to a new
 * `:core:feed` module.
 */
internal interface FeedRepository {
    suspend fun getTimeline(
        cursor: String?,
        limit: Int = TIMELINE_PAGE_LIMIT,
    ): Result<TimelinePage>
}

/**
 * One paginated page of timeline items. Each entry is a [FeedItemUi] —
 * either a [FeedItemUi.Single] standalone post or a
 * [FeedItemUi.ReplyCluster] carrying root + parent + leaf for cross-author
 * reply rendering. [nextCursor] is the cursor to pass on the next request
 * to fetch the page after this one; `null` signals end-of-feed.
 */
internal data class TimelinePage(
    val feedItems: ImmutableList<FeedItemUi>,
    val nextCursor: String?,
)

/**
 * Default page size for timeline requests. The lexicon allows 1–100; 30
 * matches Bluesky's official client default and keeps memory + scroll
 * pressure modest.
 */
internal const val TIMELINE_PAGE_LIMIT: Int = 30
