package net.kikin.nubecita.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.FeedPostEntity

/**
 * DAO for the `feed_post` table. All reads are scoped to a single
 * `(accountDid, feedType, feedUri)` partition and ordered by `position`
 * (ascending = newest-first, as written by the RemoteMediator). Reads return
 * `Flow`/`PagingSource`; writes are `suspend`.
 *
 * [pagingSource] is the app-feed read path (consumed via a `Pager` in
 * `:core:feed-cache`); [head] is the widget read path (no Paging).
 */
@Dao
interface FeedPostDao {
    @Upsert
    suspend fun upsert(posts: List<FeedPostEntity>)

    @Query(
        "SELECT * FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "ORDER BY position",
    )
    fun pagingSource(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): PagingSource<Int, FeedPostEntity>

    @Query(
        "SELECT * FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "ORDER BY position LIMIT :limit",
    )
    fun head(
        accountDid: String,
        feedType: String,
        feedUri: String,
        limit: Int,
    ): Flow<List<FeedPostEntity>>

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
     * Rows with the highest `position` are newest, so this deletes every row
     * whose `position` is at or below `MAX(position) - cap` — leaving exactly
     * the top [cap] positions. A no-op when the partition holds `<= cap` rows
     * (the threshold is below the minimum `position`).
     */
    @Query(
        "DELETE FROM feed_post " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position <= (" +
            "SELECT MAX(position) - :cap FROM feed_post " +
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
