package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
 * a [FeedItemUi.Single] standalone post, a [FeedItemUi.ReplyCluster]
 * carrying root + parent + leaf for cross-author reply rendering, or a
 * [FeedItemUi.SelfThreadChain] grouping consecutive same-author self-
 * replies. [nextCursor] is the cursor to pass on the next request to
 * fetch the page after this one; `null` signals end-of-feed.
 *
 * [wirePosts] is the page's raw [FeedViewPost] entries in their wire
 * order (before any chain projection / dedupe). The feed VM's page-
 * boundary chain merge reads `reply.parent.uri` off the head entry to
 * decide whether the current `feedItems` tail can extend across the
 * pagination cut. Internal to `:feature:feed:impl`; not exposed to any
 * other module. Per `add-feed-same-author-thread-chain` design Decision
 * 3, widening `TimelinePage` is the cleanest seam — UI models stay
 * pure, and the merge step gets the exact wire data it needs.
 */
internal data class TimelinePage(
    val feedItems: ImmutableList<FeedItemUi>,
    val nextCursor: String?,
    /**
     * Defaults to empty so test fixtures that don't exercise the chain
     * merge path (most existing VM tests) compile unchanged. Production
     * call sites in `DefaultFeedRepository` always populate this from
     * the wire response.
     */
    val wirePosts: ImmutableList<FeedViewPost> = persistentListOf(),
)

/**
 * Default page size for timeline requests. The lexicon allows 1–100; 30
 * matches Bluesky's official client default and keeps memory + scroll
 * pressure modest.
 */
internal const val TIMELINE_PAGE_LIMIT: Int = 30
