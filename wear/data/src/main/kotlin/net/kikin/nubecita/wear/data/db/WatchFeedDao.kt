package net.kikin.nubecita.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchFeedDao {
    @Query("SELECT * FROM watch_feed WHERE account_did = :accountDid ORDER BY feed_key")
    fun observeFeeds(accountDid: String): Flow<List<WatchFeedEntity>>

    @Query("SELECT * FROM watch_post WHERE account_did = :accountDid AND feed_key = :feedKey ORDER BY position")
    fun observePosts(
        accountDid: String,
        feedKey: String,
    ): Flow<List<WatchPostEntity>>

    @Query("SELECT published_at_ms FROM watch_feed WHERE account_did = :accountDid AND feed_key = :feedKey")
    suspend fun publishedAtMs(
        accountDid: String,
        feedKey: String,
    ): Long?

    /** Replaces one feed's rows in a single transaction, so the UI never sees a half-written feed. */
    @Transaction
    suspend fun replaceFeed(
        feed: WatchFeedEntity,
        posts: List<WatchPostEntity>,
    ) {
        deletePosts(feed.accountDid, feed.feedKey)
        upsertFeed(feed)
        insertPosts(posts)
    }

    @Transaction
    suspend fun deleteFeed(
        accountDid: String,
        feedKey: String,
    ) {
        deletePosts(accountDid, feedKey)
        deleteFeedRow(accountDid, feedKey)
    }

    /** Drops every account but [accountDid] (null drops all): sign-out or account switch. */
    @Transaction
    suspend fun keepOnlyAccount(accountDid: String?) {
        deletePostsExcept(accountDid.orEmpty())
        deleteFeedsExcept(accountDid.orEmpty())
    }

    @Upsert
    suspend fun upsertFeed(feed: WatchFeedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<WatchPostEntity>)

    @Query("DELETE FROM watch_post WHERE account_did = :accountDid AND feed_key = :feedKey")
    suspend fun deletePosts(
        accountDid: String,
        feedKey: String,
    )

    @Query("DELETE FROM watch_feed WHERE account_did = :accountDid AND feed_key = :feedKey")
    suspend fun deleteFeedRow(
        accountDid: String,
        feedKey: String,
    )

    @Query("DELETE FROM watch_post WHERE account_did != :accountDid")
    suspend fun deletePostsExcept(accountDid: String)

    @Query("DELETE FROM watch_feed WHERE account_did != :accountDid")
    suspend fun deleteFeedsExcept(accountDid: String)
}
