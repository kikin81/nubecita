package net.kikin.nubecita.core.feedcache

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.database.NubecitaDatabase
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi

/**
 * Pages the persisted projection, keyed on `position`.
 *
 * Room's generated `PagingSource` cannot be used here, and the reason is the
 * single most important behavioural constraint in this change. The
 * `InvalidationTracker` is table-level, so **any** write invalidates the
 * source — including a write that added rows above the reader without touching
 * anything they can see. Paging then reloads using `getRefreshKey`, and for
 * the generated source that key is an **offset**: after N rows are inserted
 * above, the same offset points N items further down. The viewport jumps,
 * which this change treats as worse than having no feature at all.
 *
 * A position key does not move. Positions are assigned once and never
 * renumbered — a prepend writes negatives above, an append writes upward from
 * the maximum — so reloading at a position returns the reader to the same item
 * regardless of what arrived around it.
 *
 * [initialPosition] is how a restored scroll anchor enters Paging: the `Pager`
 * starts at that key, so the first frame is already in the right place rather
 * than being scrolled there afterwards.
 *
 * Invalidation is wired by hand for the same reason the source is: the
 * generated one would have done it, this one must. The observer is removed when
 * the source is invalidated, because a `PagingSource` is single-use and Paging
 * creates a fresh one.
 */
internal class FeedItemPagingSource(
    private val feedKey: FeedKey,
    private val feedItemDao: FeedItemDao,
    private val cachedPostDao: CachedPostDao,
    database: NubecitaDatabase,
    private val initialPosition: Int?,
    private val viewerDid: () -> String?,
    private val prefs: () -> ModerationPrefs,
) : PagingSource<Int, FeedItemUi>() {
    private val observer =
        object : InvalidationTracker.Observer(
            arrayOf("feed_item", "feed_item_member", "cached_post"),
        ) {
            override fun onInvalidated(tables: Set<String>) = invalidate()
        }

    init {
        registerInvalidatedCallback { database.invalidationTracker.removeObserver(observer) }
        database.invalidationTracker.addObserver(observer)
    }

    /**
     * The key to reload at after an invalidation.
     *
     * Resolved to the **position** of the item nearest where the reader is,
     * taken from the loaded pages rather than from an index into them. An
     * offset would be the bug this class exists to avoid.
     */
    override fun getRefreshKey(state: PagingState<Int, FeedItemUi>): Int? {
        val anchor = state.anchorPosition ?: return initialPosition
        val item = state.closestItemToPosition(anchor) ?: return initialPosition
        return positionCache[item.key] ?: initialPosition
    }

    /**
     * Item key → stored position, for [getRefreshKey].
     *
     * `FeedItemUi` carries no position — it is a UI model — so the mapping is
     * remembered as rows are read. Bounded by what has actually been loaded.
     */
    private val positionCache = mutableMapOf<String, Int>()

    /**
     * `CancellationException` is rethrown rather than reported.
     *
     * Paging cancels a load whenever the reader scrolls past what it asked
     * for, which is routine. Collapsing that into `LoadResult.Error` would
     * surface an error state for an ordinary scroll — and a bare
     * `runCatching` here would do exactly that, since it catches
     * cancellation like anything else.
     */
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedItemUi> =
        try {
            when (params) {
                is LoadParams.Prepend -> loadUpwards(params.key, params.loadSize)
                else -> loadDownwards(params.key, params.loadSize)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // Exception, not Throwable: an OutOfMemoryError or a
            // StackOverflowError is not something a feed page can recover from
            // by showing a retry button, and swallowing it into LoadResult
            // would hide it from Crashlytics.
            LoadResult.Error(exception)
        }

    /** Refresh and append: `position` ascending from the key. */
    private suspend fun loadDownwards(
        key: Int?,
        loadSize: Int,
    ): LoadResult<Int, FeedItemUi> {
        val from = key ?: initialPosition ?: minPosition() ?: return empty()
        // One extra row tells us whether another page exists without a second
        // query, and is dropped before mapping.
        val rows = feedItemDao.itemsFrom(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, from, loadSize + 1)
        val page = rows.take(loadSize)
        val hasMore = rows.size > loadSize
        return LoadResult.Page(
            data = page.toUi(),
            // Only offer an upward key when rows actually exist above, or
            // Paging asks for a page that cannot be filled.
            prevKey = page.firstOrNull()?.position?.takeIf { hasRowsAbove(it) },
            nextKey = if (hasMore) rows[loadSize].position else null,
        )
    }

    /** Prepend: the window above the key, returned in display order. */
    private suspend fun loadUpwards(
        key: Int,
        loadSize: Int,
    ): LoadResult<Int, FeedItemUi> {
        val rows =
            feedItemDao
                .itemsBefore(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, key, loadSize)
                .reversed()
        return LoadResult.Page(
            data = rows.toUi(),
            prevKey = rows.firstOrNull()?.position?.takeIf { hasRowsAbove(it) },
            nextKey = key,
        )
    }

    private suspend fun hasRowsAbove(position: Int): Boolean = (minPosition() ?: position) < position

    private suspend fun minPosition(): Int? = feedItemDao.minPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)

    /**
     * Rehydrates rows into UI items, dropping any that cannot be rendered.
     *
     * A seam row maps to null here and is therefore absent from the paged
     * feed; the consuming feature reads it separately and renders its own
     * item, because a seam has no place in the shared sealed type.
     */
    private suspend fun List<FeedItemEntity>.toUi(): List<FeedItemUi> {
        if (isEmpty()) return emptyList()
        // One query for the whole page. Per-row lookups made this scale with
        // page size — 26 queries to render 25 items — on the read path a
        // 120 Hz scroll depends on.
        val members =
            feedItemDao.membersOfAll(
                feedKey.accountDid,
                feedKey.feedType.name,
                feedKey.feedUri,
                map { it.position },
            )
        val postsByUri =
            cachedPostDao
                .byUris(feedKey.accountDid, members.map { it.uri }.distinct())
                .associateBy { it.uri }
        val membersByPosition = members.groupBy { it.position }
        val viewer = viewerDid()
        val moderation = prefs()
        return mapNotNull { row ->
            row
                .toFeedItemUi(
                    members = membersByPosition[row.position].orEmpty(),
                    postsByUri = postsByUri,
                    viewerDid = viewer,
                    prefs = moderation,
                )?.also { positionCache[it.key] = row.position }
        }
    }

    private fun empty(): LoadResult<Int, FeedItemUi> = LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
}
