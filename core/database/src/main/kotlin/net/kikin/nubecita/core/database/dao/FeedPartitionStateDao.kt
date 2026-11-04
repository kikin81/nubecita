package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import net.kikin.nubecita.core.database.model.FeedPartitionStateEntity

/** DAO for `feed_partition_state` — one row per cached feed partition. */
@Dao
interface FeedPartitionStateDao {
    @Upsert
    suspend fun upsert(state: FeedPartitionStateEntity)

    @Query(
        "SELECT * FROM feed_partition_state " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun state(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): FeedPartitionStateEntity?

    /**
     * Records the preferences a projection was built with.
     *
     * A targeted update rather than a whole-row write, for the same reason the
     * cursors use one: this row will also carry the scroll anchor, and writing
     * the whole row from one concern would clear the other's.
     */
    @Query(
        "UPDATE feed_partition_state SET prefs_fingerprint = :fingerprint " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun updatePrefsFingerprint(
        accountDid: String,
        feedType: String,
        feedUri: String,
        fingerprint: String?,
    )

    @Query("DELETE FROM feed_partition_state WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
