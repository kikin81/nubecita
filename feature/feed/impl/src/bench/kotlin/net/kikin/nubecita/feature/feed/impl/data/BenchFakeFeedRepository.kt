package net.kikin.nubecita.feature.feed.impl.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import timber.log.Timber
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor stand-in for [FeedRepository]. Reads
 * `feature/feed/impl/src/bench/assets/timeline.json` once via the
 * [Json] parser, maps it through [BenchTimelineMapper] into the
 * production-shaped `:data:models` UI types, and caches the resulting
 * [TimelinePage] for the lifetime of the process.
 *
 * Named with the `Bench` prefix to disambiguate from the androidTest
 * `FakeFeedRepository` in
 * `feature/feed/impl/src/androidTest/.../testing/FakeFeedRepository.kt`
 * — they live in different packages (`data` vs `testing`) and source
 * sets but the short-name collision confused IDE autocomplete + grep.
 *
 * Threading:
 *
 * - [getTimeline] hops onto [IoDispatcher] before opening the asset and
 *   running [Json.decodeFromStream] — the production
 *   [DefaultFeedRepository] does the same with the same qualifier.
 *   Without the dispatcher hop the JSON parse runs inline on the
 *   `viewModelScope` (Main.immediate) coroutine, blocking the UI
 *   thread for ~5-15 ms on a Pixel 10 Pro during the bench cold-start
 *   window — i.e. inside the very TTID measurement the bench flavor
 *   exists to capture.
 *
 * Caching:
 *
 * - Successful parses are cached for the process lifetime in
 *   [cachedSuccess].
 * - Failed parses are NOT cached — every call re-attempts. This is
 *   the inverse of a `by lazy { runCatching { ... } }`, which would
 *   pin a Result.failure indefinitely and make the in-app Retry
 *   button a no-op until process death. With this retry-on-failure
 *   shape, a fixture-author edit that fixes a broken JSON gets
 *   picked up on the next `getTimeline` call without an app restart.
 *
 * Per-feed content:
 *
 * - [getTimeline] (Following) serves `timeline.json`; [getFeed] for the
 *   Discover generator serves a distinct `discover.json` so the
 *   feed-switcher journey shows visibly different posts when the user
 *   switches feeds (and screenshots prove the switch, not just a scroll
 *   reset). Any other generator URI and every list feed fall back to
 *   `timeline.json` — the bench fallback chip set only pins Following +
 *   Discover, so unknown generators / lists are unreachable today but
 *   still render deterministically rather than empty. Each asset is
 *   parsed and cached independently (see Caching).
 *
 * Pagination:
 *
 * - All calls return the full asset-backed page in one shot. The
 *   `cursor` parameter is accepted (to satisfy the [FeedRepository]
 *   interface) but ignored — `nextCursor` is null, so
 *   `FeedViewModel.loadMore` short-circuits via its `endReached`
 *   guard before ever issuing a second-page call. The bench journey
 *   scrolls the loaded page; pagination variance is not what the
 *   journey measures.
 *
 * Known limitations:
 *
 * - [TimelinePage.wirePosts] is always [persistentListOf]. The
 *   production page-boundary chain-merge in `FeedViewModel` reads
 *   `reply.parent.uri` off the head wire entry to decide whether the
 *   current `feedItems` tail can extend across the pagination cut.
 *   With an empty `wirePosts` and a single-page bench feed, the
 *   chain-merge branch is naturally unreachable. The fixture today
 *   ships no consecutive same-author posts; any future bench journey
 *   targeting `SelfThreadChain` cross-page absorption needs a wire
 *   shape, which the bench DTO layer doesn't model. Tracked as a
 *   follow-up under nubecita-xh99 (see PR description).
 *
 * Error handling:
 *
 * - Missing asset (build-time packaging miss → [FileNotFoundException])
 *   is caught and rethrown as a [FixtureLoadException] so
 *   `FeedViewModel.toFeedError` maps it to
 *   `FeedError.Unknown(cause = ...)` with a fixture-flavored message
 *   instead of the misleading `FeedError.Network` ("Connection
 *   problem") that a raw `IOException` would produce.
 * - Parse / mapper failures surface as `Result.failure(...)`. The
 *   user-visible InitialError → Retry path re-attempts the load,
 *   per the no-cache-failures policy above.
 *
 * See `bd show nubecita-xh99` for the broader scope (crmi.6 Section A2).
 */
@Singleton
internal class BenchFakeFeedRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : FeedRepository {
        private val mutex = Mutex()

        // Per-asset success cache. ConcurrentHashMap (not a plain Map +
        // @Volatile) so the lock-free fast-path read below is memory-safe
        // under the concurrent first-loads that a feed switch can trigger.
        // Failures are never inserted, preserving the original retry-on-
        // failure semantics (a fixture-author fix is picked up on the next
        // call without an app restart).
        private val cache = ConcurrentHashMap<String, TimelinePage>()

        override suspend fun getTimeline(
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> = pageForAsset(TIMELINE_ASSET_PATH)

        override suspend fun getFeed(
            feedUri: String,
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> = pageForAsset(assetForFeedUri(feedUri))

        // No list is pinned in the bench fallback chip set, so this path is
        // unreachable today; serve the Following fixture deterministically
        // rather than empty if a future bench journey pins a list.
        override suspend fun getListFeed(
            listUri: String,
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> = pageForAsset(TIMELINE_ASSET_PATH)

        /**
         * Resolve a feed-generator URI to its bench fixture asset. The
         * Discover (`whats-hot`) generator gets its own [DISCOVER_ASSET_PATH]
         * so the chip-switch journey renders visibly different content;
         * everything else falls back to the Following fixture.
         */
        private fun assetForFeedUri(feedUri: String): String =
            when (feedUri) {
                PinnedFeedsRepository.DISCOVER_FEED_URI -> DISCOVER_ASSET_PATH
                else -> TIMELINE_ASSET_PATH
            }

        private suspend fun pageForAsset(assetPath: String): Result<TimelinePage> =
            withContext(dispatcher) {
                cache[assetPath]?.let { return@withContext Result.success(it) }
                mutex.withLock {
                    // Re-check under the lock — another coroutine may have
                    // populated this asset's cache entry while we waited.
                    cache[assetPath]?.let { return@withLock Result.success(it) }
                    runCatching { loadFromAsset(assetPath) }
                        .onSuccess { cache[assetPath] = it }
                        .onFailure { Timber.tag(TAG).e(it, "Failed to load bench feed: %s", assetPath) }
                }
            }

        private fun loadFromAsset(assetPath: String): TimelinePage {
            val stream =
                try {
                    context.assets.open(assetPath)
                } catch (e: FileNotFoundException) {
                    // Translate to a non-IOException type so
                    // FeedViewModel.toFeedError doesn't misclassify a
                    // build-time packaging miss as a connectivity error.
                    throw FixtureLoadException(
                        "Bench fixture not packaged: assets/$assetPath not found. " +
                            "Verify :feature:feed:impl's bench source set includes the asset.",
                        e,
                    )
                }
            val dto =
                stream.use { input ->
                    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
                    JSON.decodeFromStream(BenchTimelineDto.serializer(), input)
                }
            return TimelinePage(
                feedItems = BenchTimelineMapper.toFeedItems(dto).toPersistentList(),
                nextCursor = null,
                wirePosts = persistentListOf(),
            )
        }

        private companion object {
            private const val TAG = "BenchFakeFeedRepository"

            /**
             * Asset path resolved by [android.content.res.AssetManager].
             * The fixtures live at
             * `feature/feed/impl/src/bench/assets/{timeline,discover}.json`
             * — under the fake's owning module so a rename in `:app` doesn't
             * silently brick the bench feed. AGP merges every module's
             * bench-flavor assets into the bench APK's single asset tree, so
             * these relative paths resolve at runtime.
             */
            private const val TIMELINE_ASSET_PATH = "timeline.json"

            /** Discover (`whats-hot`) generator fixture — see [assetForFeedUri]. */
            private const val DISCOVER_ASSET_PATH = "discover.json"

            /**
             * Lenient JSON config:
             *
             * - `ignoreUnknownKeys = true` so future fixture extensions
             *   (`_comment`, new viewer fields the production model
             *   doesn't yet have) don't break the loader.
             * - `coerceInputValues = true` so unknown enum discriminators
             *   (`"type": "ReplyCluster"` ahead of an enum extension,
             *   `"video"` lowercase typo, etc.) coerce to the property's
             *   declared default ([BenchFeedItemDto.Type.Single] /
             *   [BenchEmbedDto.Type.Empty]) instead of throwing
             *   `SerializationException`. The mapper's per-item
             *   `runCatching` containment then surfaces the issue as a
             *   per-card visual (Single → text-only, Empty → no embed)
             *   plus a Timber warning, instead of bricking the whole
             *   timeline.
             *
             *   Trade-off: an explicit JSON `null` on a non-nullable
             *   Kotlin field that has a default (`"likeCount": null`
             *   on `BenchStatsDto.likeCount: Int = 0`) silently
             *   produces the default. Acceptable for checked-in
             *   fixture data — `null` isn't an intentional encoding
             *   here.
             */
            private val JSON =
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
        }
    }

/**
 * Surfaced when the bench fixture asset is missing at runtime. Lives
 * outside the `IOException` hierarchy so `FeedViewModel.toFeedError`
 * doesn't misclassify a build-time packaging miss as a connectivity
 * error.
 */
internal class FixtureLoadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
