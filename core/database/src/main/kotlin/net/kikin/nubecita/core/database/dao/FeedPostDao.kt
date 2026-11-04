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
     * Keeps the newest [cap] rows of the partition and deletes the rest.
     * **Lowest `position` is newest** (position 0 is the top of the newest
     * page; older APPEND posts get higher positions), so this deletes every row
     * whose `position` is at or above `MIN(position) + cap` — leaving exactly
     * the newest [cap] positions. A no-op when the partition holds `<= cap` rows
     * (the threshold is above the maximum `position`).
     */
    @Query(
        "DELETE FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position >= (" +
            "SELECT MIN(position) + :cap FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri)",
    )
    suspend fun trimToCap(
        accountDid: String,
        feedType: String,
        feedUri: String,
        cap: Int,
    )

    @Query("DELETE FROM feed_post WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
