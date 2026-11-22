package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.feature.search.impl.DiscoverFeedUi
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus

/**
 * Everything the Search tab's Explore content shows, in one value: trending
 * topics, suggested feeds (each with its lazily loaded preview) and suggested
 * accounts, each with its own load status so one endpoint failing marks only its
 * own section.
 *
 * [accounts] are as the server returned them, minus the ones dismissed this
 * session; the viewer's own follow actions are NOT in them — they live in
 * [FollowOverlay], and readers apply that. Pinned state is not in [feeds] either:
 * it comes from the pinned-feeds stream, and readers apply that.
 */
internal data class ExploreContent(
    val trends: ImmutableList<TrendUi> = persistentListOf(),
    val trendsStatus: DiscoverSectionStatus = DiscoverSectionStatus.Idle,
    val feeds: ImmutableList<DiscoverFeedUi> = persistentListOf(),
    val feedsStatus: DiscoverSectionStatus = DiscoverSectionStatus.Idle,
    val accounts: ImmutableList<SuggestedAccountUi> = persistentListOf(),
    val accountsStatus: DiscoverSectionStatus = DiscoverSectionStatus.Idle,
)

/**
 * The Explore data surface, held once per process so every presenter showing it
 * (the landing, and the second pane on wide windows) reads the same [content] and
 * can never disagree. Presenters only project it.
 *
 * Implementations:
 * - Production: [DefaultExploreRepository] — `app.bsky.unspecced.getTrends`,
 *   `getSuggestedFeeds`, `getSuggestedUsersForExplore` and `app.bsky.feed.getFeed`.
 * - Bench: `BenchFakeExploreRepository` — deterministic in-memory fixture, no network.
 */
internal interface ExploreRepository {
    /** The shared content. Reset when the signed-in account changes or signs out. */
    val content: StateFlow<ExploreContent>

    /**
     * Loads the sections that are not [DiscoverSectionStatus.Loaded] (every section
     * when [force]). Concurrent callers share one in-flight load; once everything
     * is loaded and [force] is false this does nothing. Returns when the load settles;
     * a section failing is reflected in [content], never thrown.
     */
    suspend fun load(force: Boolean = false)

    /**
     * Fetches the first posts of the feed at [feedUri] for its preview strip, once:
     * a feed whose preview is already loading, loaded or failed is left alone.
     */
    suspend fun loadFeedPreview(feedUri: String)

    /** Removes [did] from the suggested accounts for the rest of this account's session. */
    fun dismissAccount(did: String)

    /** The DID for [actor], which is a DID already (returned as is) or a handle (resolved). */
    suspend fun resolveDid(actor: String): Result<String>
}
