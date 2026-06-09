package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity

/**
 * DAO for the `feed_remote_keys` table — the per-partition Paging 3 cursor.
 * One row per `(accountDid, feedType, feedUri)` partition; [upsert] overwrites
 * the cursor on each `REFRESH`/`APPEND`.
 */
@Dao
interface FeedRemoteKeyDao {
    @Upsert
    suspend fun upsert(key: FeedRemoteKeyEntity)

    @Query(
        "SELECT * FROM feed_remote_keys " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun remoteKey(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): FeedRemoteKeyEntity?

    @Query("DELETE FROM feed_remote_keys WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
