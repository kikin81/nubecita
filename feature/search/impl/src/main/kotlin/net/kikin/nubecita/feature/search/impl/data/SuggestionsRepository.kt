package net.kikin.nubecita.feature.search.impl.data

/**
 * Discover-tab data surface: suggested accounts, suggested feeds, and a
 * lightweight feed-preview strip.
 *
 * All three methods are one-shot (no cursor / pagination): the Discover tab
 * shows a curated sample — it does not page-load the full suggestions list
 * like the Search-tab infinite scroll surfaces.
 *
 * Implementations:
 * - Production: [DefaultSuggestionsRepository] — real XRPC calls via
 *   `app.bsky.actor.getSuggestions`, `app.bsky.feed.getSuggestedFeeds`,
 *   and `app.bsky.feed.getFeed`.
 * - Bench: [BenchFakeSuggestionsRepository] — deterministic in-memory fixture,
 *   zero network calls.
 */
internal interface SuggestionsRepository {
    /**
     * Returns a list of suggested actors to follow, mapped to
     * [SuggestedAccountUi]. Default limit matches the lexicon's
     * recommended sample size for the onboarding / discover surface.
     */
    suspend fun getSuggestedAccounts(limit: Int = 15): Result<List<SuggestedAccountUi>>

    /**
     * Returns a list of suggested feed generators, mapped to
     * [SuggestedFeedUi]. Default limit matches the lexicon's
     * recommended sample size.
     */
    suspend fun getSuggestedFeeds(limit: Int = 15): Result<List<SuggestedFeedUi>>

    /**
     * Fetches the first [limit] posts from the feed at [feedUri] and
     * maps them to [FeedPreviewPostUi] for the Discover-tab preview strip.
     */
    suspend fun getFeedPreview(
        feedUri: String,
        limit: Int = 3,
    ): Result<List<FeedPreviewPostUi>>
}
