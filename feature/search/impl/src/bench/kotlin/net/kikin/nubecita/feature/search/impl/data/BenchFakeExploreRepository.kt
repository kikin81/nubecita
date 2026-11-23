package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.feature.search.impl.DiscoverFeedUi
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.FeedPreviewStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor stand-in for [ExploreRepository]. Serves deterministic,
 * marketing-quality fixture data with zero network calls, so the bench screenshot
 * journey can drive the Explore content to a populated, stable state.
 *
 * Behavior:
 * - [load] fills every section from the fixtures below in one shot; [force] re-fills
 *   them (keeping loaded previews and honouring dismissals).
 * - [loadFeedPreview] fills the preview strip with [previewPosts] for any suggested feed.
 * - [resolveDid] resolves the handles the fixtures use ([handleDids]) and fails any other.
 * - Never fails otherwise — the bench journey targets the populated Loaded state,
 *   not the error/retry path.
 *
 * The six [trends] cover every link shape [parseTrendLink] reads (a feed by DID, a
 * feed by handle, a starter pack, a search, an unknown shape, and a second feed by
 * DID). The feed links point at the same feed URIs as the suggested [feeds], which
 * the bench feed fake can open.
 *
 * Avatars are left null: accounts and feeds without a custom avatar fall back to
 * `NubecitaAsyncImage`'s standard flat placeholder tile.
 */
@Singleton
internal class BenchFakeExploreRepository
    @Inject
    constructor() : ExploreRepository {
        private val state = MutableStateFlow(ExploreContent())
        private val dismissed = mutableSetOf<String>()

        override val content: StateFlow<ExploreContent> = state.asStateFlow()

        override suspend fun load(force: Boolean) {
            state.update { current ->
                val priorByUri = current.feeds.associateBy { it.feed.uri }
                current.copy(
                    trends = trends.usableTrends().toImmutableList(),
                    trendsStatus = DiscoverSectionStatus.Loaded,
                    feeds =
                        feeds
                            .map { feed ->
                                priorByUri[feed.uri] ?: DiscoverFeedUi(feed = feed)
                            }.toImmutableList(),
                    feedsStatus = DiscoverSectionStatus.Loaded,
                    accounts = accounts.filter { it.did !in dismissed }.toImmutableList(),
                    accountsStatus = DiscoverSectionStatus.Loaded,
                )
            }
        }

        override suspend fun loadFeedPreview(feedUri: String) {
            val known = state.value.feeds.any { it.feed.uri == feedUri }
            if (!known) return
            state.update { current ->
                current.copy(
                    feeds =
                        current.feeds
                            .map {
                                if (it.feed.uri == feedUri && it.previewStatus == FeedPreviewStatus.Idle) {
                                    it.copy(preview = previewPosts.toImmutableList(), previewStatus = FeedPreviewStatus.Loaded)
                                } else {
                                    it
                                }
                            }.toImmutableList(),
                )
            }
        }

        override fun dismissAccount(did: String) {
            dismissed += did
            state.update { it.copy(accounts = it.accounts.filter { account -> account.did != did }.toImmutableList()) }
        }

        override suspend fun resolveDid(actor: String): Result<String> =
            if (actor.startsWith("did:")) {
                Result.success(actor)
            } else {
                handleDids[actor]?.let { Result.success(it) }
                    ?: Result.failure(IllegalArgumentException("bench fake cannot resolve $actor"))
            }

        private val handleDids: Map<String, String> =
            mapOf(
                "labnotes.bsky.social" to "did:plc:bench1",
                "studio.bsky.social" to "did:plc:bench2",
                "shipit.bsky.social" to "did:plc:bench3",
            )

        private val trends: List<TrendUi> =
            listOf(
                TrendUi(
                    topic = "bench-science",
                    displayName = "Webb telescope results",
                    link = "/profile/did:plc:bench1/feed/science",
                    category = "science",
                    postCount = 12_400L,
                    isHot = true,
                ),
                TrendUi(
                    topic = "bench-art",
                    displayName = "Typeface releases",
                    link = "/profile/studio.bsky.social/feed/art",
                    category = "art",
                    postCount = 3_100L,
                    isHot = false,
                ),
                TrendUi(
                    topic = "bench-starter-pack",
                    displayName = "Science starter pack",
                    link = "/starter-pack/labnotes.bsky.social/3kbench",
                    category = null,
                    postCount = 870L,
                    isHot = false,
                ),
                TrendUi(
                    topic = "bench-eclipse",
                    displayName = "Lunar eclipse",
                    link = "/search?q=lunar%20eclipse",
                    category = "news",
                    postCount = 25_000L,
                    isHot = true,
                ),
                TrendUi(
                    topic = "bench-unknown",
                    displayName = "Shipping in public",
                    link = "/hashtag/shipping",
                    category = "tech",
                    postCount = 640L,
                    isHot = false,
                ),
                TrendUi(
                    topic = "bench-dev",
                    displayName = "Indie dev launches",
                    link = "/profile/did:plc:bench3/feed/dev",
                    category = "tech",
                    postCount = 1_950L,
                    isHot = false,
                ),
            )

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
