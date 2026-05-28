package net.kikin.nubecita.feature.feed.impl.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor stand-in for [FeedRepository]. Reads
 * `app/src/bench/assets/timeline.json` once via the [Json] parser,
 * maps it through [BenchTimelineMapper] into the production-shaped
 * `:data:models` UI types, and caches the resulting [TimelinePage]
 * for the lifetime of the process.
 *
 * Scoping:
 *
 * - `@Singleton` so the JSON parse happens at most once per app
 *   lifetime — the bench journey is supposed to be deterministic and
 *   re-parsing on every `getTimeline` call would add ~1–2 ms of jank
 *   onto every paginated fetch. The production [DefaultFeedRepository]
 *   is intentionally NOT `@Singleton` (per-injection lifetime is
 *   harmless for an XRPC-driven stateless wrapper), but the bench fake
 *   owns its own scope decision because the cached fixture is its
 *   only meaningful state.
 *
 * Pagination model:
 *
 * - Cursor-`null` → return the full asset-backed page in one shot, with
 *   `nextCursor = null` to signal end-of-feed. This is intentionally
 *   simpler than the production AppView's segment-by-segment pagination
 *   — the Macrobench journey scrolls the loaded page; pagination
 *   variance is not what the journey measures.
 * - Cursor-non-null → return an empty page (caller has already seen
 *   the only page). [TimelinePage.nextCursor] stays null, so
 *   `FeedViewModel`'s next-page guard short-circuits and never asks
 *   again.
 *
 * Error handling:
 *
 * - Parse failures (malformed JSON, missing required field, etc.)
 *   surface as `Result.failure(...)` — `FeedViewModel.toFeedError`
 *   maps this into [net.kikin.nubecita.feature.feed.impl.FeedError.Unknown]
 *   and the host renders the InitialError UI. Better signal than a
 *   silently-empty feed when someone breaks the fixture; the bench
 *   journey can't proceed without a valid fixture anyway.
 *
 * See `bd show nubecita-xh99` for the broader scope (crmi.6 Section A2).
 */
@Singleton
internal class FakeFeedRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FeedRepository {
        /**
         * Lenient JSON config:
         *
         * - `ignoreUnknownKeys = true` so future fixture extensions (a
         *   `_comment` field, new viewer fields the production model
         *   doesn't yet have) don't break the loader.
         * - `coerceInputValues = true` so a null in the JSON for a
         *   non-nullable Kotlin field falls back to the default value
         *   rather than throwing.
         */
        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        private val cachedPage: Result<TimelinePage> by lazy {
            runCatching {
                val raw =
                    context.assets
                        .open(TIMELINE_ASSET_PATH)
                        .bufferedReader()
                        .use { it.readText() }
                val dto = json.decodeFromString(BenchTimelineDto.serializer(), raw)
                TimelinePage(
                    feedItems = BenchTimelineMapper.toFeedItems(dto).toPersistentList(),
                    nextCursor = null,
                    wirePosts = persistentListOf(),
                )
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to load bench timeline from %s", TIMELINE_ASSET_PATH)
            }
        }

        override suspend fun getTimeline(
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> {
            // Subsequent (cursor-non-null) calls land on end-of-feed
            // immediately — see KDoc above for the rationale.
            if (cursor != null) return Result.success(EMPTY_PAGE)
            return cachedPage
        }

        private companion object {
            private const val TAG = "FakeFeedRepository"

            /**
             * Asset path resolved by [android.content.res.AssetManager].
             * The fixture lives under `app/src/bench/assets/timeline.json`
             * today; AGP merges every module's bench-flavor assets into
             * the single asset tree of the bench-flavor APK, so this
             * relative path resolves regardless of which module bundled
             * the file.
             */
            private const val TIMELINE_ASSET_PATH = "timeline.json"

            private val EMPTY_PAGE =
                TimelinePage(
                    feedItems = persistentListOf(),
                    nextCursor = null,
                    wirePosts = persistentListOf(),
                )
        }
    }
