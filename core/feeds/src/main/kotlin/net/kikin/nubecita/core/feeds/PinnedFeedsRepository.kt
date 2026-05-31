package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import timber.log.Timber
import javax.inject.Inject

/**
 * Outcome of [PinnedFeedsRepository.loadPinnedFeeds].
 *
 * [usedFallback] is `true` when the result is the local `[Following,
 * Discover]` default (no `savedFeedsPrefV2`, an empty pinned set, or a
 * `getPreferences` failure). [error] carries the non-fatal throwable when a
 * network call failed — the feeds list is still populated (with the fallback
 * or whatever could be hydrated) so the Feed stays usable, but the caller
 * can surface a one-shot "couldn't refresh feeds" signal.
 */
public data class PinnedFeedsResult(
    val feeds: ImmutableList<PinnedFeedUi>,
    val usedFallback: Boolean,
    val error: Throwable? = null,
)

/**
 * Reads the user's pinned feeds from their `app.bsky.actor.getPreferences`
 * `savedFeedsPrefV2` and projects them to the UI-ready [PinnedFeedUi] chip
 * model, hydrating generator display-name/avatar via `getFeedGenerators`.
 *
 * This is the only layer that reads saved-feeds preferences; feature modules
 * depend on `:core:feeds`, never on `getPreferences` directly. The future
 * Feeds-management epic reuses this read path.
 */
public interface PinnedFeedsRepository {
    /**
     * Loads the ordered pinned-feed chip set. Never throws on the happy
     * path: a `getPreferences` failure (or no/empty saved feeds) yields the
     * `[Following, Discover]` fallback with [PinnedFeedsResult.usedFallback]
     * set, and any network error is reported via [PinnedFeedsResult.error].
     */
    public suspend fun loadPinnedFeeds(): PinnedFeedsResult

    /**
     * Validates a persisted last-selected feed [uri] against the live
     * [pinned] set, returning it when still present or falling back to
     * [FOLLOWING_FEED_URI] otherwise (including a `null` persisted value).
     */
    public fun validateSelectedFeedUri(
        uri: String?,
        pinned: List<PinnedFeedUi>,
    ): String

    public companion object {
        /**
         * Sentinel URI for the synthesized Following timeline entry. The
         * Following feed is not a generator, so it has no `at://` URI; this
         * stable token identifies it in selection/persistence and matches
         * the `value` of a `type="timeline"` saved feed.
         */
        public const val FOLLOWING_FEED_URI: String = "following"

        /**
         * The well-known `whats-hot` ("Discover") feed-generator URI, used as
         * the second default chip alongside Following. Hardcoded by design
         * (documented in the feed-switching change's design D3 / Risks).
         */
        public const val DISCOVER_FEED_URI: String =
            "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"
    }
}

internal class DefaultPinnedFeedsRepository
    @Inject
    constructor(
        private val dataSource: FeedsDataSource,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PinnedFeedsRepository {
        override suspend fun loadPinnedFeeds(): PinnedFeedsResult =
            withContext(dispatcher) {
                val items =
                    try {
                        dataSource.getSavedFeedItems()
                    } catch (throwable: Throwable) {
                        // Non-fatal: getPreferences failed (offline, auth
                        // refresh, etc.). Fall back to the local defaults but
                        // tell the caller so it can surface a soft error.
                        Timber.tag(TAG).e(throwable, "getPreferences failed: %s", throwable.javaClass.name)
                        return@withContext fallbackResult(error = throwable)
                    }

                // Dedupe `type="timeline"` pins so two identical Following chips
                // can't appear (all timeline entries collapse to the single
                // synthesized Following feed); keep the first, drop later dupes.
                var sawTimeline = false
                val pinned =
                    items
                        .orEmpty()
                        .filter(SavedFeed::pinned)
                        .filter { item ->
                            if (item.type != TYPE_TIMELINE) {
                                true
                            } else if (sawTimeline) {
                                false
                            } else {
                                sawTimeline = true
                                true
                            }
                        }
                if (pinned.isEmpty()) {
                    // No savedFeedsPrefV2 entry, or nothing pinned → defaults.
                    return@withContext fallbackResult(error = null)
                }

                // Batch-hydrate only the generator pins; Following/List entries
                // need no network call.
                val generatorUris = pinned.filter { it.type == TYPE_FEED }.map(SavedFeed::value)
                var hydrationError: Throwable? = null
                val generatorsByUri =
                    if (generatorUris.isEmpty()) {
                        emptyMap()
                    } else {
                        try {
                            dataSource.getFeedGenerators(generatorUris).associateBy(GeneratorMeta::uri)
                        } catch (throwable: Throwable) {
                            // Non-fatal: drop the un-hydratable generators but
                            // keep Following/List chips so the Feed still works.
                            Timber.tag(TAG).e(
                                throwable,
                                "getFeedGenerators failed: %s",
                                throwable.javaClass.name,
                            )
                            hydrationError = throwable
                            emptyMap()
                        }
                    }

                val feeds =
                    pinned.mapNotNull { item -> item.toPinnedFeedUi(generatorsByUri) }.toImmutableList()
                PinnedFeedsResult(feeds = feeds, usedFallback = false, error = hydrationError)
            }

        override fun validateSelectedFeedUri(
            uri: String?,
            pinned: List<PinnedFeedUi>,
        ): String =
            uri
                ?.takeIf { candidate -> pinned.any { it.uri == candidate } }
                ?: PinnedFeedsRepository.FOLLOWING_FEED_URI

        private fun fallbackResult(error: Throwable?): PinnedFeedsResult = PinnedFeedsResult(feeds = fallbackFeeds(), usedFallback = true, error = error)

        private fun SavedFeed.toPinnedFeedUi(generatorsByUri: Map<String, GeneratorMeta>): PinnedFeedUi? =
            when (type) {
                TYPE_TIMELINE ->
                    PinnedFeedUi(
                        id = id,
                        uri = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        kind = FeedKind.Following,
                        displayName = FOLLOWING_DISPLAY_NAME,
                        // Renders the local Home glyph — no remote avatar.
                        avatarUrl = null,
                    )

                TYPE_FEED -> {
                    // A pinned generator whose metadata couldn't be hydrated is
                    // dropped (mapNotNull) rather than rendered nameless.
                    val meta = generatorsByUri[value] ?: return null
                    PinnedFeedUi(
                        id = id,
                        uri = value,
                        kind = FeedKind.Generator,
                        displayName = meta.displayName,
                        avatarUrl = meta.avatarUrl,
                    )
                }

                TYPE_LIST ->
                    PinnedFeedUi(
                        id = id,
                        uri = value,
                        kind = FeedKind.List,
                        // List metadata is hydrated by the lists sheet, not the
                        // chip-row load; the URI is enough to identify the pin.
                        displayName = value,
                        avatarUrl = null,
                    )

                // Unknown future SavedFeed.type → ignore.
                else -> null
            }

        internal companion object {
            private const val TAG = "PinnedFeedsRepo"
            private const val TYPE_TIMELINE = "timeline"
            private const val TYPE_FEED = "feed"
            private const val TYPE_LIST = "list"
            private const val FOLLOWING_DISPLAY_NAME = "Following"
            private const val DISCOVER_DISPLAY_NAME = "Discover"

            /**
             * The offline / new-account default chip set: Following plus
             * Discover, both rendered from local glyphs (no hydration call).
             */
            internal fun fallbackFeeds(): ImmutableList<PinnedFeedUi> =
                persistentListOf(
                    PinnedFeedUi(
                        id = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        uri = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        kind = FeedKind.Following,
                        displayName = FOLLOWING_DISPLAY_NAME,
                        avatarUrl = null,
                    ),
                    PinnedFeedUi(
                        id = "whats-hot",
                        uri = PinnedFeedsRepository.DISCOVER_FEED_URI,
                        kind = FeedKind.Generator,
                        displayName = DISCOVER_DISPLAY_NAME,
                        // Default renders the local LocalFireDepartment glyph.
                        avatarUrl = null,
                    ),
                )
        }
    }
