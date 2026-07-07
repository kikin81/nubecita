package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.github.kikin81.atproto.app.bsky.actor.SavedFeedsPrefV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.database.dao.SavedFeedDao
import net.kikin.nubecita.core.database.model.SavedFeedEntity
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import timber.log.Timber
import javax.inject.Inject

/**
 * Outcome of [PinnedFeedsRepository.observePinnedFeeds] emissions.
 *
 * [usedFallback] is `true` when the result is the local `[Following,
 * Discover]` default (no cached feeds, or nothing pinned). [error] carries
 * the non-fatal throwable when a network call failed — the feeds list is
 * still populated (with the fallback or whatever could be hydrated) so the
 * Feed stays usable, but the caller can surface a one-shot "couldn't refresh
 * feeds" signal.
 */
public data class PinnedFeedsResult(
    val feeds: ImmutableList<PinnedFeedUi>,
    val usedFallback: Boolean,
    val error: Throwable? = null,
)

/**
 * Reads the user's pinned feeds from the Room cache and keeps them
 * up-to-date via write-through refreshes against the network.
 *
 * This is the only layer that reads saved-feeds preferences; feature modules
 * depend on `:core:feeds`, never on `getPreferences` directly. The future
 * Feeds-management epic reuses this read path.
 */
public interface PinnedFeedsRepository {
    /**
     * Observes the ordered pinned-feed chip set from the local Room cache.
     * Emits immediately with whatever is cached (or the `[Following,
     * Discover]` fallback when the cache is empty / nothing is pinned), then
     * re-emits whenever [refresh] writes through new data.
     *
     * Never throws: an empty cache yields a fallback result with
     * [PinnedFeedsResult.usedFallback] set to `true`.
     */
    public fun observePinnedFeeds(): Flow<PinnedFeedsResult>

    /**
     * Fetches the latest saved feeds from the network, hydrates generator
     * metadata, and writes through to the Room cache via a diff-upsert
     * (`upsert` + `deleteUrisNotIn`, or `clear` when zero feeds resolve).
     *
     * Returns [Result.failure] — without touching the cache — when
     * `getSavedFeedItems` or `getFeedGenerators` throws (network/auth
     * error), so the cache is preserved for offline use.
     */
    public suspend fun refresh(): Result<Unit>

    /**
     * Validates a persisted last-selected feed [uri] against the live
     * [pinned] set, returning it when still present or falling back to
     * [FOLLOWING_FEED_URI] otherwise (including a `null` persisted value).
     */
    public fun validateSelectedFeedUri(
        uri: String?,
        pinned: List<PinnedFeedUi>,
    ): String

    /**
     * Non-destructively pins the feed at [uri].
     *
     * If [uri] already exists in `savedFeedsPrefV2.items`, its `pinned` flag
     * is set to `true`. If it is absent, a new `SavedFeed(type="feed")` entry
     * is appended. Foreign preference entries (moderation prefs, label prefs,
     * etc.) are preserved. Writes through to the Room cache optimistically;
     * rolls back on `putPreferences` failure.
     *
     * Returns [Result.failure] on network/auth error. `CancellationException`
     * propagates and is never wrapped.
     */
    public suspend fun pinFeed(uri: String): Result<Unit>

    /**
     * Non-destructively unpins the feed at [uri].
     *
     * The `SavedFeed` entry is **kept** in `savedFeedsPrefV2.items` with
     * `pinned = false` — it is never deleted, so feeds saved on another client
     * are not lost. Foreign preference entries are preserved. Writes through
     * to the Room cache optimistically; rolls back on `putPreferences` failure.
     *
     * Returns [Result.failure] on network/auth error. `CancellationException`
     * propagates and is never wrapped.
     */
    public suspend fun unpinFeed(uri: String): Result<Unit>

    /**
     * Reorders the user's pinned feeds to match [orderedPinnedUris] (the room
     * URIs — [FOLLOWING_FEED_URI] for the timeline, the `at://` URI otherwise).
     *
     * The current preferences are re-read at commit time so a feed pinned on
     * another client is merged rather than clobbered: any server-pinned URI
     * absent from [orderedPinnedUris] is appended in server order (never
     * dropped). Unpinned saved entries are preserved after the pinned block,
     * and foreign preferences survive the round-trip. Writes the new positions
     * through to the Room cache optimistically; rolls back on `putPreferences`
     * failure. A no-op (unchanged order) skips the network write entirely.
     *
     * Returns [Result.failure] on network/auth error. `CancellationException`
     * propagates and is never wrapped.
     */
    public suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit>

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
        private val dao: SavedFeedDao,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PinnedFeedsRepository {
        // Serializes all read-modify-write operations (pinFeed, unpinFeed) so
        // concurrent calls cannot clobber each other with a stale server read.
        private val writeMutex = Mutex()

        // -------------------------------------------------------------------------
        // Read path: Room cache → PinnedFeedUi
        // -------------------------------------------------------------------------

        override fun observePinnedFeeds(): Flow<PinnedFeedsResult> =
            dao.observeSavedFeeds().map { entities ->
                val pinnedFeeds =
                    entities
                        .filter { it.pinned }
                        .mapNotNull { it.toPinnedFeedUi() }
                        .toImmutableList()
                if (pinnedFeeds.isEmpty()) {
                    PinnedFeedsResult(feeds = fallbackFeeds(), usedFallback = true)
                } else {
                    PinnedFeedsResult(feeds = pinnedFeeds, usedFallback = false)
                }
            }

        // -------------------------------------------------------------------------
        // Write path: network → Room cache (diff-upsert)
        // -------------------------------------------------------------------------

        override suspend fun refresh(): Result<Unit> =
            withContext(dispatcher) {
                runCatching { doRefresh() }
                    .onFailure { if (it is CancellationException) throw it }
            }

        private suspend fun doRefresh() {
            val items =
                try {
                    dataSource.getSavedFeedItems()
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "getSavedFeedItems failed: %s", throwable.javaClass.name)
                    throw throwable // propagate → runCatching → Result.failure
                }

            // Dedupe `type="timeline"` so two identical timeline entries don't
            // collide on the same "following" PK (keep first, drop later).
            var sawTimeline = false
            val deduped =
                items.orEmpty().filter { item ->
                    if (item.type != TYPE_TIMELINE) {
                        true
                    } else if (sawTimeline) {
                        false
                    } else {
                        sawTimeline = true
                        true
                    }
                }

            // Batch-hydrate only the generator feeds; Following and List entries
            // don't need a separate network call.
            val generatorUris = deduped.filter { it.type == TYPE_FEED }.map(SavedFeed::value).distinct()
            val generatorsByUri: Map<String, GeneratorMeta> =
                if (generatorUris.isEmpty()) {
                    emptyMap()
                } else {
                    try {
                        dataSource.getFeedGenerators(generatorUris).associateBy(GeneratorMeta::uri)
                    } catch (throwable: Throwable) {
                        // getFeedGenerators threw — abort entirely so the cache
                        // (which may have valid stale data) is not wiped.
                        Timber.tag(TAG).w(
                            throwable,
                            "getFeedGenerators failed: %s",
                            throwable.javaClass.name,
                        )
                        throw throwable // propagate → runCatching → Result.failure
                    }
                }

            // Read existing cached rows once so we can fall back to their metadata
            // when getFeedGenerators returns a partial response (some feeds not returned).
            // Only needed when there are generator feeds to hydrate.
            val existingRows: Map<String, SavedFeedEntity> =
                if (generatorUris.isEmpty()) {
                    emptyMap()
                } else {
                    dao.getAllOnce().associateBy { it.uri }
                }

            // Build display-ready entities. For unresolved generators (not in the
            // server's getFeedGenerators response), fall back to the existing cached
            // row's metadata, or the URI string as last resort. A feed that IS in
            // saved prefs is NEVER dropped here — the prefs list is canonical.
            val entities =
                deduped.mapIndexedNotNull { index, item ->
                    item.toEntity(
                        position = index,
                        generatorsByUri = generatorsByUri,
                        existingRows = existingRows,
                    )
                }

            // Canonical URI set from saved prefs: used as the prune key so that
            // only feeds removed from the user's preferences are deleted from Room.
            // Unknown types (future extensions) are excluded as they were never written.
            val prefUris =
                deduped
                    .mapNotNull { item ->
                        when (item.type) {
                            TYPE_TIMELINE -> PinnedFeedsRepository.FOLLOWING_FEED_URI
                            TYPE_FEED, TYPE_LIST -> item.value
                            else -> null
                        }
                    }.distinct()

            // Cache-safe write-through:
            // • When 0 pref URIs, use clear() — deleteUrisNotIn([]) crashes Room.
            // • Otherwise upsert resolved rows first, then prune by the prefs set.
            if (prefUris.isEmpty()) {
                dao.clear()
            } else {
                dao.upsert(entities)
                dao.deleteUrisNotIn(prefUris)
            }
        }

        // -------------------------------------------------------------------------
        // Pin / unpin: read-modify-write via putPreferences + Room write-through
        // -------------------------------------------------------------------------

        override suspend fun pinFeed(uri: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    writeMutex.withLock {
                        val fullPrefs = dataSource.getFullPreferences()
                        val currentItems =
                            extractSavedFeedItems(fullPrefs)
                                .orEmpty()
                                .toMutableList()

                        val existingIndex = currentItems.indexOfFirst { it.value == uri }
                        val isNew = existingIndex < 0
                        val priorPinned = if (isNew) false else currentItems[existingIndex].pinned

                        // Early return: feed already present and pinned — skip network write.
                        if (!isNew && priorPinned) return@withLock

                        val newItems: List<SavedFeed> =
                            if (!isNew) {
                                currentItems.toMutableList().also { items ->
                                    items[existingIndex] = items[existingIndex].copy(pinned = true)
                                }
                            } else {
                                currentItems +
                                    SavedFeed(
                                        id = Tid.next(),
                                        type = TYPE_FEED,
                                        value = uri,
                                        pinned = true,
                                    )
                            }

                        // Optimistic Room write (before server confirmation; rolled back on failure).
                        if (!isNew) {
                            dao.setPinned(uri, true)
                        } else {
                            dao.upsert(
                                listOf(
                                    SavedFeedEntity(
                                        uri = uri,
                                        // Brief placeholder until the next refresh() hydrates real metadata.
                                        // Use the record key (last path segment) instead of the full AT URI
                                        // so the chip shows a readable label immediately.
                                        displayName = uri.feedRkeyOrSelf(),
                                        creatorHandle = null,
                                        avatarUrl = null,
                                        pinned = true,
                                        position = newItems.size - 1,
                                    ),
                                ),
                            )
                        }

                        try {
                            dataSource.putPreferences(mergeSavedFeedsPrefs(fullPrefs, newItems))
                        } catch (t: Throwable) {
                            // Rollback: restore Room to the state before the optimistic write.
                            // For NEW items, delete the row entirely — setPinned(uri, false) would
                            // leave a phantom row (pinned=false) in the cache until the next refresh().
                            // For EXISTING items, flip the flag back to its prior value.
                            if (isNew) {
                                dao.deleteByUri(uri)
                            } else {
                                dao.setPinned(uri, priorPinned)
                            }
                            throw t
                        }
                    }
                }.onFailure { if (it is CancellationException) throw it }
            }

        override suspend fun unpinFeed(uri: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    writeMutex.withLock {
                        val fullPrefs = dataSource.getFullPreferences()
                        val currentItems =
                            extractSavedFeedItems(fullPrefs)
                                .orEmpty()
                                .toMutableList()

                        val existingIndex = currentItems.indexOfFirst { it.value == uri }
                        val priorPinned = if (existingIndex >= 0) currentItems[existingIndex].pinned else false

                        // Early return: feed not saved at all, or already unpinned — skip network write.
                        if (existingIndex < 0 || !priorPinned) return@withLock

                        // Non-destructive: KEEP the SavedFeed in items, only set pinned=false.
                        // A feed saved on another client must not be deleted from the array.
                        val newItems: List<SavedFeed> =
                            currentItems.toMutableList().also { items ->
                                items[existingIndex] = items[existingIndex].copy(pinned = false)
                            }

                        // Optimistic Room write.
                        dao.setPinned(uri, false)

                        try {
                            dataSource.putPreferences(mergeSavedFeedsPrefs(fullPrefs, newItems))
                        } catch (t: Throwable) {
                            dao.setPinned(uri, priorPinned)
                            throw t
                        }
                    }
                }.onFailure { if (it is CancellationException) throw it }
            }

        override suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    writeMutex.withLock {
                        val fullPrefs = dataSource.getFullPreferences()
                        val currentItems = extractSavedFeedItems(fullPrefs).orEmpty()

                        val pinned = currentItems.filter { it.pinned }
                        val unpinned = currentItems.filter { !it.pinned }
                        val pinnedByRoomUri = pinned.associateBy { it.roomUri() }

                        // 1. The user's explicit order — only feeds still pinned server-side.
                        val ordered = orderedPinnedUris.mapNotNull { pinnedByRoomUri[it] }
                        val orderedRoomUris = ordered.mapTo(mutableSetOf()) { it.roomUri() }
                        // 2. Any server-pinned feed absent from the local list (pinned on
                        //    another client after load) — appended in server order, never dropped.
                        val extra = pinned.filterNot { it.roomUri() in orderedRoomUris }
                        // 3. Unpinned saved entries preserved after the pinned block.
                        val newItems = ordered + extra + unpinned

                        val priorOrder = currentItems.map { it.roomUri() }
                        val newOrder = newItems.map { it.roomUri() }

                        // No-op: resulting order unchanged → skip the network + Room writes.
                        if (newOrder == priorOrder) return@withLock

                        // Optimistic Room position rewrite (rolled back on failure).
                        dao.updatePositions(newOrder)
                        try {
                            dataSource.putPreferences(mergeSavedFeedsPrefs(fullPrefs, newItems))
                        } catch (t: Throwable) {
                            dao.updatePositions(priorOrder)
                            throw t
                        }
                    }
                }.onFailure { if (it is CancellationException) throw it }
            }

        // -------------------------------------------------------------------------
        // Shared helpers
        // -------------------------------------------------------------------------

        /**
         * The Room primary key for this wire [SavedFeed]: [FOLLOWING_FEED_URI]
         * for the timeline entry (whose Room row is keyed on the sentinel),
         * the `value` (`at://` URI) for feed / list entries.
         */
        private fun SavedFeed.roomUri(): String = if (type == TYPE_TIMELINE) PinnedFeedsRepository.FOLLOWING_FEED_URI else value

        override fun validateSelectedFeedUri(
            uri: String?,
            pinned: List<PinnedFeedUi>,
        ): String =
            uri
                ?.takeIf { candidate -> pinned.any { it.uri == candidate } }
                ?: PinnedFeedsRepository.FOLLOWING_FEED_URI

        // -------------------------------------------------------------------------
        // Mapping: SavedFeedEntity → PinnedFeedUi (entity never escapes this module)
        // -------------------------------------------------------------------------

        /**
         * Maps a cached entity to [PinnedFeedUi], inferring [FeedKind] from
         * the URI shape. Returns `null` for unrecognised URI schemes so that
         * unknown future entry types are silently dropped rather than crashing.
         */
        private fun SavedFeedEntity.toPinnedFeedUi(): PinnedFeedUi? {
            val kind = uriToFeedKind(uri) ?: return null
            return PinnedFeedUi(
                id = uri,
                uri = uri,
                kind = kind,
                displayName = displayName,
                avatarUrl = avatarUrl,
            )
        }

        // -------------------------------------------------------------------------
        // Mapping: SavedFeed (wire) → SavedFeedEntity (Room)
        // -------------------------------------------------------------------------

        /**
         * Converts one wire-level [SavedFeed] to a [SavedFeedEntity] at the
         * given position in the user's saved-feed list.
         *
         * For generator entries whose metadata was not returned by `getFeedGenerators`
         * (a partial server response), the method merges in the best available
         * metadata: fresh server data > existing cached row > URI string fallback.
         * A generator that IS in the user's saved prefs is NEVER dropped — only
         * truly unrecognised future types are returned as `null`.
         *
         * Returns `null` only for unrecognised types.
         */
        private fun SavedFeed.toEntity(
            position: Int,
            generatorsByUri: Map<String, GeneratorMeta>,
            existingRows: Map<String, SavedFeedEntity>,
        ): SavedFeedEntity? =
            when (type) {
                TYPE_TIMELINE ->
                    SavedFeedEntity(
                        uri = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        displayName = FOLLOWING_DISPLAY_NAME,
                        creatorHandle = null,
                        avatarUrl = null,
                        pinned = pinned,
                        position = position,
                    )

                TYPE_FEED -> {
                    val meta = generatorsByUri[value]
                    val existing = existingRows[value]
                    SavedFeedEntity(
                        uri = value,
                        // Prefer fresh metadata; fall back to cached row; last resort = record key.
                        displayName = meta?.displayName ?: existing?.displayName ?: value.feedRkeyOrSelf(),
                        creatorHandle = meta?.creatorHandle ?: existing?.creatorHandle,
                        avatarUrl = meta?.avatarUrl ?: existing?.avatarUrl,
                        pinned = pinned,
                        position = position,
                    )
                }

                TYPE_LIST ->
                    SavedFeedEntity(
                        uri = value,
                        // List display names are not available from getPreferences;
                        // use the record key as a readable placeholder.
                        displayName = value.feedRkeyOrSelf(),
                        creatorHandle = null,
                        avatarUrl = null,
                        pinned = pinned,
                        position = position,
                    )

                // Unknown future type → drop silently.
                else -> null
            }

        internal companion object {
            private const val TAG = "PinnedFeedsRepo"

            /**
             * Returns the last path segment of an AT URI (the record key), e.g.
             * `"whats-hot"` from `"at://did:plc:…/app.bsky.feed.generator/whats-hot"`.
             * Falls back to the full string when there is no `/` or the segment is blank.
             * Used for readable placeholder display names before server metadata arrives.
             */
            internal fun String.feedRkeyOrSelf(): String = substringAfterLast('/').ifBlank { this }

            private const val TYPE_TIMELINE = "timeline"
            internal const val TYPE_FEED = "feed"
            private const val TYPE_LIST = "list"
            private const val FOLLOWING_DISPLAY_NAME = "Following"
            private const val DISCOVER_DISPLAY_NAME = "Discover"

            /**
             * Builds a complete `putPreferences` list preserving every foreign preference
             * entry from [original] (moderation prefs, label prefs, unmodelled future
             * entries carried as [GetPreferencesResponsePreferencesUnion.Unknown]) while
             * replacing the [SavedFeedsPrefV2] entry with one backed by [newItems].
             *
             * If [original] contained no [SavedFeedsPrefV2], the new entry is appended.
             */
            internal fun mergeSavedFeedsPrefs(
                original: List<GetPreferencesResponsePreferencesUnion>,
                newItems: List<SavedFeed>,
            ): List<PutPreferencesRequestPreferencesUnion> {
                val preserved =
                    original
                        .filterNot { it is SavedFeedsPrefV2 }
                        .map { it.asPutPreference() }
                return preserved + SavedFeedsPrefV2(items = newItems)
            }

            /**
             * Infers [FeedKind] from the URI shape:
             * - `"following"` → [FeedKind.Following]
             * - contains `/app.bsky.feed.generator/` → [FeedKind.Generator]
             * - contains `/app.bsky.graph.list/` → [FeedKind.List]
             * - anything else → `null` (drop the entity)
             */
            internal fun uriToFeedKind(uri: String): FeedKind? =
                when {
                    uri == PinnedFeedsRepository.FOLLOWING_FEED_URI -> FeedKind.Following
                    uri.contains("/app.bsky.feed.generator/") -> FeedKind.Generator
                    uri.contains("/app.bsky.graph.list/") -> FeedKind.List
                    else -> null
                }

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
