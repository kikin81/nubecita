package net.kikin.nubecita.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.CachedFeedEntry
import net.kikin.nubecita.core.database.model.FeedPostEntity

/**
 * DAO for the `feed_post` table. All reads are scoped to a single
 * `(accountDid, feedType, feedUri)` partition and ordered by `position`
 * ascending, which is **newest-first**: the RemoteMediator writes position 0
 * at the top of the newest page and higher positions to older (APPEND) posts.
 * Reads return `Flow`/`PagingSource`; writes are `suspend`.
 *
 * [pagingSource] is the app-feed read path (consumed via a `Pager` in
 * `:core:feed-cache`); [head] is the widget read path (no Paging). Both join
 * `cached_post`, because a `feed_post` row carries only a reference to its
 * post — the post itself is stored once per account and shared with the
 * entries that use it as reply context. The join is an INNER one: a partition
 * row with no stored post should not exist (both are written in one
 * transaction) and is better absent than rendered as a half-row.
 */
@Dao
interface FeedPostDao {
    @Upsert
    suspend fun upsert(posts: List<FeedPostEntity>)

    @Query(
        "SELECT fp.position AS position, fp.uri AS uri, fp.reposted_by_did AS reposted_by_did, fp.reposted_by_label AS reposted_by_label, fp.reply_root_uri AS reply_root_uri, fp.reply_parent_uri AS reply_parent_uri, cp.account_did AS p_account_did, cp.uri AS p_uri, cp.cid AS p_cid, cp.author_did AS p_author_did, cp.indexed_at AS p_indexed_at, cp.text AS p_text, cp.post_blob AS p_post_blob, cp.unavailable AS p_unavailable " +
            "FROM feed_post fp " +
            "JOIN cached_post cp ON cp.account_did = fp.account_did AND cp.uri = fp.uri " +
            "WHERE fp.account_did = :accountDid AND fp.feed_type = :feedType AND fp.feed_uri = :feedUri " +
            "ORDER BY fp.position",
    )
    fun pagingSource(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): PagingSource<Int, CachedFeedEntry>

    @Query(
        "SELECT fp.position AS position, fp.uri AS uri, fp.reposted_by_did AS reposted_by_did, fp.reposted_by_label AS reposted_by_label, fp.reply_root_uri AS reply_root_uri, fp.reply_parent_uri AS reply_parent_uri, cp.account_did AS p_account_did, cp.uri AS p_uri, cp.cid AS p_cid, cp.author_did AS p_author_did, cp.indexed_at AS p_indexed_at, cp.text AS p_text, cp.post_blob AS p_post_blob, cp.unavailable AS p_unavailable " +
            "FROM feed_post fp " +
            "JOIN cached_post cp ON cp.account_did = fp.account_did AND cp.uri = fp.uri " +
            "WHERE fp.account_did = :accountDid AND fp.feed_type = :feedType AND fp.feed_uri = :feedUri " +
            "ORDER BY fp.position LIMIT :limit",
    )
    fun head(
        accountDid: String,
        feedType: String,
        feedUri: String,
        limit: Int,
    ): Flow<List<CachedFeedEntry>>

    /**
     * Every envelope in the partition, in order — the input to a local
     * re-projection, which rebuilds the grouping from stored rows rather than
     * refetching and so keeps the reader's place.
     *
     * Envelopes only: the posts come from the by-URI store, keyed off these
     * rows' `uri` and the reply URIs in their context blob.
     */
    @Query(
        "SELECT * FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "ORDER BY position",
    )
    suspend fun entriesIn(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): List<FeedPostEntity>

    @Query(
        "SELECT MAX(position) FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun maxPosition(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): Int?

    @Query(
        "DELETE FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun clearPartition(
        accountDid: String,
        feedType: String,
        feedUri: String,
    )

    /**
     * The position of the row [offset] places down from the newest, or `null`
     * when the partition holds fewer rows than that.
     *
     * The trim's cut-off, counted in **rows**. The arithmetic this replaced —
     * `DELETE WHERE position >= MIN(position) + cap` — retained a range of
     * `cap` position *values*, which is the same thing only while positions are
     * dense. A prepend allocates inside a reserved window and leaves the unused
     * end of it empty, so gaps are normal and that form kept far fewer entries
     * than the cap (design D17).
     */
    @Query(
        "SELECT position FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "ORDER BY position LIMIT 1 OFFSET :offset",
    )
    suspend fun positionAtOffsetFromTop(
        accountDid: String,
        feedType: String,
        feedUri: String,
        offset: Int,
    ): Int?

    /** Deletes everything older than [position], which itself survives. */
    @Query(
        "DELETE FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position > :position",
    )
    suspend fun deleteOlderThan(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    )

    /**
     * The oldest position among the entries that the partition's surviving
     * projection rows still reference.
     *
     * Entry positions and projection positions are different coordinate
     * spaces: a self-thread chain folds several entries into one item, so the
     * two cannot be trimmed to the same number and stay consistent. Tying them
     * by **content** instead keeps every entry the surviving items were built
     * from, which is what local re-projection needs to rebuild them (D27).
     */
    @Query(
        "SELECT MAX(position) FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND uri IN (" +
            "SELECT uri FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri)",
    )
    suspend fun oldestReferencedByProjection(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): Int?

    /**
     * The partition's newest position — the lowest value, since position 0 is
     * the top of the newest REFRESH page and a prepend goes negative.
     *
     * A prepend allocates its reserved window relative to this rather than
     * assuming `-1`, so a catch-up into an already-prepended partition cannot
     * collide with the batch above it (design D17).
     */
    @Query(
        "SELECT MIN(position) FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun minPosition(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): Int?

    /**
     * The bottom of the prepend block: the highest negative position in the
     * partition, or `null` when nothing has been prepended.
     *
     * This is how an interrupted multi-page catch-up finds where to continue.
     * The fill writes contiguously from the top of its window, so the position
     * after this one is the next free slot — and deriving it from the rows
     * means a fill that resumes after process death lands below the pages that
     * already landed rather than on top of them.
     */
    @Query(
        "SELECT MAX(position) FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position < 0",
    )
    suspend fun maxNegativePosition(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): Int?

    /**
     * The lowest occupied position at or above [from], or `null` if nothing is
     * stored there.
     *
     * The prepend's room check. The reserved window bounds one fill, but the
     * round budget is per-`load()`, so a fill interrupted and resumed often
     * enough could otherwise walk past its window — and because the write is
     * an `@Upsert` keyed on `position`, that would silently overwrite stored
     * entries instead of failing. Asking the database what is still free turns
     * that into a stop condition.
     */
    @Query(
        "SELECT MIN(position) FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position >= :from",
    )
    suspend fun firstPositionAtOrAbove(
        accountDid: String,
        feedType: String,
        feedUri: String,
        from: Int,
    ): Int?

    /**
     * Removes every entry showing [uri], in every partition of the account.
     *
     * Every partition, because a post can sit in several feeds at once, and
     * every entry within one, because each repost of a post is its own entry.
     */
    @Query("DELETE FROM feed_post WHERE account_did = :accountDid AND uri = :uri")
    suspend fun deleteEntriesFor(
        accountDid: String,
        uri: String,
    )

    @Query("DELETE FROM feed_post WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
