package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor stand-in for [SuggestionsRepository]. Returns deterministic,
 * marketing-quality fixture data regardless of the requested limit, so the
 * bench screenshot journey can drive the Discover tab to a populated,
 * stable state without any network call.
 *
 * Named with the `Bench` prefix to disambiguate from any future unit-test
 * fake (same pattern as [BenchFakeSearchFeedsRepository]).
 *
 * Behavior:
 * - [getSuggestedAccounts] ignores [limit]; returns the full [accounts]
 *   fixture in one shot.
 * - [getSuggestedFeeds] ignores [limit]; returns the full [feeds] fixture
 *   in one shot.
 * - [getFeedPreview] ignores [feedUri] and [limit]; returns [previewPosts].
 * - Never fails — the bench journey targets the populated Loaded state,
 *   not the error/retry path.
 *
 * Avatars are left null: accounts and feeds without a custom avatar fall
 * back to `NubecitaAsyncImage`'s standard flat placeholder tile.
 */
@Singleton
internal class BenchFakeSuggestionsRepository
    @Inject
    constructor() : SuggestionsRepository {
        override suspend fun getSuggestedAccounts(limit: Int): Result<List<SuggestedAccountUi>> = Result.success(accounts)

        override suspend fun getSuggestedFeeds(limit: Int): Result<List<SuggestedFeedUi>> = Result.success(feeds)

        override suspend fun getFeedPreview(
            feedUri: String,
            limit: Int,
        ): Result<List<FeedPreviewPostUi>> = Result.success(previewPosts)

        private val accounts: List<SuggestedAccountUi> =
            listOf(
                SuggestedAccountUi(
                    did = "did:plc:bench1",
                    handle = "science.bsky.social",
                    displayName = "Science Digest",
                    avatarUrl = null,
                    isFollowing = false,
                    followUri = null,
                    mutualsCount = 42,
                    mutualAvatarUrls = persistentListOf(),
                ),
                SuggestedAccountUi(
                    did = "did:plc:bench2",
                    handle = "designfeed.bsky.social",
                    displayName = "Design Feed",
                    avatarUrl = null,
                    isFollowing = false,
                    followUri = null,
                    mutualsCount = 17,
                    mutualAvatarUrls = persistentListOf(),
                ),
                SuggestedAccountUi(
                    did = "did:plc:bench3",
                    handle = "techweekly.bsky.social",
                    displayName = "Tech Weekly",
                    avatarUrl = null,
                    isFollowing = true,
                    followUri = "at://did:plc:me/app.bsky.graph.follow/bench3",
                    mutualsCount = 8,
                    mutualAvatarUrls = persistentListOf(),
                ),
            )

        private val feeds: List<SuggestedFeedUi> =
            listOf(
                SuggestedFeedUi(
                    uri = "at://did:plc:bench1/app.bsky.feed.generator/science",
                    displayName = "Science",
                    creatorHandle = "labnotes.bsky.social",
                    avatarUrl = null,
                    description = "Peer-reviewed papers, preprints, and the people behind them.",
                ),
                SuggestedFeedUi(
                    uri = "at://did:plc:bench2/app.bsky.feed.generator/art",
                    displayName = "Art & Design",
                    creatorHandle = "studio.bsky.social",
                    avatarUrl = null,
                    description = "Illustration, type, and visual experiments from working artists.",
                ),
                SuggestedFeedUi(
                    uri = "at://did:plc:bench3/app.bsky.feed.generator/dev",
                    displayName = "Indie Dev",
                    creatorHandle = "shipit.bsky.social",
                    avatarUrl = null,
                    description = "Solo founders and small teams shipping in public.",
                    isPinned = true,
                ),
            )

        private val previewPosts: List<FeedPreviewPostUi> =
            listOf(
                FeedPreviewPostUi(
                    authorHandle = "scientist.bsky.social",
                    authorAvatarUrl = null,
                    text = "Fascinating new results from the Webb telescope — thread 🧵",
                    thumbnailUrl = null,
                ),
                FeedPreviewPostUi(
                    authorHandle = "designer.bsky.social",
                    authorAvatarUrl = null,
                    text = "Just shipped a new typeface. Sneak peek below 👀",
                    thumbnailUrl = null,
                ),
                FeedPreviewPostUi(
                    authorHandle = "devlog.bsky.social",
                    authorAvatarUrl = null,
                    text = "Day 30 of building in public. Reached 100 users today!",
                    thumbnailUrl = null,
                ),
            )
    }
