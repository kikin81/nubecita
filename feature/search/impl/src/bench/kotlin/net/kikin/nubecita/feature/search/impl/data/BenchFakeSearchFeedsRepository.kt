package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor stand-in for [SearchFeedsRepository]. Returns a fixed,
 * marketing-quality set of custom feeds regardless of the query string, so
 * the bench screenshot journey can drive the Feeds tab to a populated,
 * deterministic state without any network call.
 *
 * Named with the `Bench` prefix to disambiguate from the `src/test`
 * [FakeSearchFeedsRepository] (gate-driven, for unit tests).
 *
 * Behavior:
 *
 * - [searchFeeds] ignores `query`, `cursor`, and `limit`: it always
 *   returns the full [feeds] fixture in a single page with
 *   `nextCursor = null`, so `SearchFeedsViewModel.loadMore` short-circuits
 *   via its `endReached` guard before issuing a second-page call.
 * - Never fails — the bench journey targets the populated Loaded state,
 *   not the error/retry path.
 *
 * Avatars are left null: feeds without a custom icon fall back to
 * `NubecitaAsyncImage`'s standard flat placeholder tile, which keeps the
 * fixture self-contained (no per-feed asset to ship) while still rendering
 * a complete row.
 */
@Singleton
internal class BenchFakeSearchFeedsRepository
    @Inject
    constructor() : SearchFeedsRepository {
        override suspend fun searchFeeds(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchFeedsPage> =
            Result.success(
                SearchFeedsPage(
                    items = feeds.toImmutableList(),
                    nextCursor = null,
                ),
            )

        private val feeds: List<FeedGeneratorUi> =
            listOf(
                feed(
                    slug = "science",
                    displayName = "Science",
                    creatorHandle = "labnotes.bsky.social",
                    creatorDisplayName = "Lab Notes",
                    description = "Peer-reviewed papers, preprints, and the people behind them.",
                    likeCount = 48_902L,
                ),
                feed(
                    slug = "art-design",
                    displayName = "Art & Design",
                    creatorHandle = "studio.bsky.social",
                    creatorDisplayName = "The Studio",
                    description = "Illustration, type, and visual experiments from working artists.",
                    likeCount = 36_551L,
                ),
                feed(
                    slug = "trail-running",
                    displayName = "Trail Running",
                    creatorHandle = "switchback.bsky.social",
                    creatorDisplayName = "Switchback",
                    description = "Routes, race reports, and ridgelines from the ultra community.",
                    likeCount = 21_408L,
                ),
                feed(
                    slug = "coffee",
                    displayName = "Coffee",
                    creatorHandle = "thirdwave.bsky.social",
                    creatorDisplayName = "Third Wave",
                    description = "Single-origin obsessions, brew methods, and roaster spotlights.",
                    likeCount = 18_730L,
                ),
                feed(
                    slug = "indie-dev",
                    displayName = "Indie Dev",
                    creatorHandle = "shipit.bsky.social",
                    creatorDisplayName = "Ship It",
                    description = "Solo founders and small teams shipping in public.",
                    likeCount = 27_115L,
                ),
                feed(
                    slug = "photography",
                    displayName = "Photography",
                    creatorHandle = "goldenhour.bsky.social",
                    creatorDisplayName = "Golden Hour",
                    description = "Street, landscape, and film photography from across the network.",
                    likeCount = 41_287L,
                ),
            )

        private companion object {
            fun feed(
                slug: String,
                displayName: String,
                creatorHandle: String,
                creatorDisplayName: String,
                description: String,
                likeCount: Long,
            ): FeedGeneratorUi =
                FeedGeneratorUi(
                    uri = "at://did:plc:$slug/app.bsky.feed.generator/$slug",
                    displayName = displayName,
                    creatorHandle = creatorHandle,
                    creatorDisplayName = creatorDisplayName,
                    description = description,
                    avatarUrl = null,
                    likeCount = likeCount,
                )
        }
    }
