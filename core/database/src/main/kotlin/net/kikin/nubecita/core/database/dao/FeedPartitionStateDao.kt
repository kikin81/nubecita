package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
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

    /**
     * Records the reader's place in this partition.
     *
     * Targeted, like [updatePrefsFingerprint] and for the same reason: the row
     * also carries the projection's fingerprint, and a whole-row write from
     * one concern would clear the other's. `INSERT OR IGNORE` first, because
     * the reader can scroll a partition whose fingerprint was never written —
     * an `UPDATE` alone would silently do nothing on a row that does not exist
     * yet, and the anchor would never be saved.
     */
    @Transaction
    suspend fun saveAnchor(
        accountDid: String,
        feedType: String,
        feedUri: String,
        itemKey: String,
        position: Int,
        offset: Int,
    ) {
        insertIfAbsent(FeedPartitionStateEntity(accountDid, feedType, feedUri))
        updateAnchor(accountDid, feedType, feedUri, itemKey, position, offset)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(state: FeedPartitionStateEntity)

    @Query(
        "UPDATE feed_partition_state SET anchor_item_key = :itemKey, " +
            "anchor_position = :position, anchor_offset = :offset " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun updateAnchor(
        accountDid: String,
        feedType: String,
        feedUri: String,
        itemKey: String?,
        position: Int?,
        offset: Int?,
    )

    /**
     * Drops the reader's place, leaving the rest of the row intact.
     *
     * The destructive refresh calls this (D8): the reader asked for a clean
     * timeline, so restoring them into the old one would be the opposite of
     * what the gesture meant — and the row the anchor named is gone anyway.
     */
    suspend fun clearAnchor(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ) = updateAnchor(accountDid, feedType, feedUri, itemKey = null, position = null, offset = null)

    /**
     * The partition's state as it changes.
     *
     * The pill is derived from this plus the rows above the reader's place, so
     * it has to re-evaluate when either moves — including when the background
     * worker writes while the app is away, which is the case the pill exists
     * for.
     */
    @Query(
        "SELECT * FROM feed_partition_state " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    fun observe(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): Flow<FeedPartitionStateEntity?>

    /**
     * Records that the reader has acknowledged everything above [position].
     *
     * Insert-if-absent first, like [saveAnchor] and for the same reason: a
     * reader can dismiss the pill on a partition whose row was never written.
     */
    @Transaction
    suspend fun dismissPillAbove(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    ) {
        insertIfAbsent(FeedPartitionStateEntity(accountDid, feedType, feedUri))
        updatePillDismissed(accountDid, feedType, feedUri, position)
    }

    @Query(
        "UPDATE feed_partition_state SET pill_dismissed_position = :position " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun updatePillDismissed(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int?,
    )

    @Query("DELETE FROM feed_partition_state WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
