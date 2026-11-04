package net.kikin.nubecita.core.feedcache

import android.content.Context
import androidx.paging.PagingData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import timber.log.Timber
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Bench-flavor [FeedRepository]: the whole feed, offline, with no session, no
 * network and no Room.
 *
 * It serves the JSON fixtures in this module's `src/bench/assets/` — the same
 * ones the feed screen's bench journeys were written against — through both
 * read paths, so the Glance widget's head and the screen's pages come from one
 * source. The fixtures moved here with the screen: once the feed reads its
 * pages from this module (D1), a fixture living in `:feature:feed:impl` could
 * no longer reach the repository that has to serve them.
 *
 * Fixtures per feed: Following serves `timeline.json`, the Discover generator
 * serves `discover.json` so a feed switch shows visibly different posts, and
 * any other generator or list falls back to `timeline.json` rather than
 * rendering empty. Each asset is parsed once and cached for the process.
 *
 * A failed parse is NOT cached, which is the inverse of a `by lazy { ... }`:
 * that would pin the failure until process death and make the in-app retry a
 * no-op, while this picks up a fixture-author's fix on the next read.
 *
 * [SAMPLE_POSTS] remains as the fallback when an asset is missing — a
 * packaging miss then degrades the widget to four posts instead of an empty
 * list that reads like a broken feed.
 *
 * The maintenance operations are no-ops: the bench cache is immutable sample
 * data, and [catchUp] reports that no bounded prepend exists, since there is no
 * network above the head to catch up on.
 */
@Singleton
internal class FakeFeedRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : FeedRepository {
        private val mutex = Mutex()

        // Per-asset success cache. ConcurrentHashMap (not a plain Map +
        // @Volatile) so the lock-free fast-path read below is memory-safe under
        // the concurrent first-loads a feed switch can trigger. Failures are
        // never inserted, preserving retry-on-failure.
        private val cache = ConcurrentHashMap<String, List<FeedItemUi>>()

        override fun pagedFeed(
            feedKey: FeedKey,
            initialPosition: Int?,
        ): Flow<PagingData<FeedItemUi>> = flow { emit(PagingData.from(itemsFor(feedKey))) }

        override fun head(
            feedKey: FeedKey,
            n: Int,
        ): Flow<List<PostUi>> = flow { emit(itemsFor(feedKey).allPosts().take(n)) }

        override suspend fun refresh(feedKey: FeedKey): Result<Boolean> = Result.success(true)

        // Offline by construction: the bench fixture has no network and no
        // boundary cursor, so there is nothing above the head to catch up on.
        override suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome> = Result.success(CatchUpOutcome.NOT_POSSIBLE)

        // The bench fixture is a fixed page with no reader state: nothing to
        // restore, and nowhere to put a saved place.

        override suspend fun anchor(feedKey: FeedKey): FeedAnchor? = null

        override suspend fun saveAnchor(
            feedKey: FeedKey,
            itemKey: String,
            offset: Int,
        ) = Unit

        override suspend fun clearAnchor(feedKey: FeedKey) = Unit

        override suspend fun trimToCap(
            feedKey: FeedKey,
            cap: Int,
            anchorItemPosition: Int?,
            ceiling: Int,
        ) = Unit

        override suspend fun clearAccount(accountDid: String) = Unit

        /**
         * The fixture post whose id is [uri], searched across every fixture.
         *
         * Backs [BenchFeedPostSource] so the bench `PostRepository.getPost`
         * resolves posts the feed rendered — a media viewer opened from a feed
         * image shows that post's images rather than the video fallback.
         */
        suspend fun findPost(uri: String): PostUi? =
            ASSETS.firstNotNullOfOrNull { asset ->
                itemsForAsset(asset).allPosts().firstOrNull { it.id == uri }
            }

        private suspend fun itemsFor(feedKey: FeedKey): List<FeedItemUi> =
            itemsForAsset(
                when {
                    feedKey.feedType == FeedType.DISCOVER || feedKey.feedUri == PinnedFeedsRepository.DISCOVER_FEED_URI ->
                        DISCOVER_ASSET
                    else -> TIMELINE_ASSET
                },
            )

        private suspend fun itemsForAsset(assetPath: String): List<FeedItemUi> =
            withContext(dispatcher) {
                cache[assetPath]?.let { return@withContext it }
                mutex.withLock {
                    // Re-check under the lock — another coroutine may have
                    // populated this entry while we waited.
                    cache[assetPath]?.let { return@withLock it }
                    val loaded =
                        runCatching { loadFromAsset(assetPath) }
                            .onFailure { Timber.tag(TAG).e(it, "Failed to load bench feed: %s", assetPath) }
                            .getOrNull()
                    if (loaded != null) {
                        cache[assetPath] = loaded
                        loaded
                    } else {
                        // Not cached: a packaging miss should be retried, and
                        // four posts beat an empty list that reads as broken.
                        SAMPLE_POSTS.map(FeedItemUi::Single)
                    }
                }
            }

        @OptIn(ExperimentalSerializationApi::class)
        private fun loadFromAsset(assetPath: String): List<FeedItemUi> {
            val stream =
                try {
                    context.assets.open(assetPath)
                } catch (e: FileNotFoundException) {
                    throw FixtureLoadException(
                        "Bench fixture not packaged: assets/$assetPath not found. " +
                            "Verify :core:feed-cache's bench source set includes the asset.",
                        e,
                    )
                }
            val dto = stream.use { input -> JSON.decodeFromStream(BenchTimelineDto.serializer(), input) }
            return BenchTimelineMapper.toFeedItems(dto)
        }

        private companion object {
            const val TAG = "BenchFeedCache"
            const val TIMELINE_ASSET = "timeline.json"
            const val DISCOVER_ASSET = "discover.json"
            val ASSETS = listOf(TIMELINE_ASSET, DISCOVER_ASSET)

            val JSON =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }

            private fun samplePost(
                didSuffix: String,
                rkey: String,
                handle: String,
                displayName: String,
                createdAt: String,
                body: String,
                stats: PostStatsUi,
            ): PostUi =
                PostUi(
                    id = "at://did:plc:$didSuffix/app.bsky.feed.post/$rkey",
                    cid = "bafyreibench${didSuffix}00000000000000000000000000000000000000",
                    author =
                        AuthorUi(
                            did = "did:plc:$didSuffix",
                            handle = handle,
                            displayName = displayName,
                            avatarUrl = "file:///android_asset/img/avatars/$didSuffix.jpg",
                        ),
                    createdAt = Instant.parse(createdAt),
                    text = body,
                    facets = persistentListOf(),
                    embed = EmbedUi.Empty,
                    stats = stats,
                    viewer = ViewerStateUi(),
                    repostedBy = null,
                )

            val SAMPLE_POSTS: List<PostUi> =
                listOf(
                    samplePost(
                        didSuffix = "benchmaya000000000000000",
                        rkey = "widgetpost001",
                        handle = "maya.bsky.social",
                        displayName = "Maya Okonkwo",
                        createdAt = "2026-07-14T15:20:00Z",
                        body = "morning light on the ridge before the trail filled up. worth the 5am alarm.",
                        stats = PostStatsUi(replyCount = 4, repostCount = 7, likeCount = 58),
                    ),
                    samplePost(
                        didSuffix = "benchleo0000000000000000",
                        rkey = "widgetpost002",
                        handle = "leo.dev",
                        displayName = "Leo Nakamura",
                        createdAt = "2026-07-14T13:05:00Z",
                        body = "shipped the offline cache today. 120hz scrolling holds even on the cheap test device. 🐳",
                        stats = PostStatsUi(replyCount = 12, repostCount = 3, likeCount = 91),
                    ),
                    samplePost(
                        didSuffix = "benchsofia00000000000000",
                        rkey = "widgetpost003",
                        handle = "sofia.art",
                        displayName = "Sofía Herrera",
                        createdAt = "2026-07-14T11:40:00Z",
                        body = "new zine drop this weekend. printed the covers by hand, they smell like ink and possibility.",
                        stats = PostStatsUi(replyCount = 6, repostCount = 15, likeCount = 132),
                    ),
                    samplePost(
                        didSuffix = "benchtomas00000000000000",
                        rkey = "widgetpost004",
                        handle = "tomas.bsky.social",
                        displayName = "Tomás ",
                        createdAt = "2026-07-14T09:15:00Z",
                        body = "reminder that the small commits compound. one honest test at a time.",
                        stats = PostStatsUi(replyCount = 2, repostCount = 1, likeCount = 27),
                    ),
                )
        }
    }

/** Every post inside each item — what the widget's head reads and the post lookup searches. */
private fun List<FeedItemUi>.allPosts(): List<PostUi> =
    flatMap { item ->
        when (item) {
            is FeedItemUi.Single -> listOf(item.post)
            is FeedItemUi.ReplyCluster -> listOf(item.root, item.parent, item.leaf)
            is FeedItemUi.SelfThreadChain -> item.posts
            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> emptyList()
        }
    }
