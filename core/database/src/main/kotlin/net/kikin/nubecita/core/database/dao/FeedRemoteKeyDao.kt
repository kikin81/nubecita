package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity

/**
 * DAO for the `feed_remote_keys` table — the per-partition pagination cursors.
 * One row per `(accountDid, feedType, feedUri)` partition.
 *
 * [upsert] writes the whole row and belongs to `REFRESH`, which legitimately
 * resets every cursor. Append and prepend use the **targeted** updates instead:
 * a whole-row write from one direction would null the other's cursors, and a
 * prepend that wiped `next_cursor` would silently break forward pagination
 * while every test that only checks its own direction still passed.
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

    /** Advances only the APPEND cursor, leaving the prepend cursors untouched. */
    @Query(
        "UPDATE feed_remote_keys SET next_cursor = :nextCursor " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun updateAppendCursor(
        accountDid: String,
        feedType: String,
        feedUri: String,
        nextCursor: String?,
    )

    /** Writes only the PREPEND cursors, leaving `next_cursor` untouched. */
    @Query(
        "UPDATE feed_remote_keys SET boundary_since = :boundarySince, " +
            "fill_cursor = :fillCursor, pending_boundary = :pendingBoundary " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun updatePrependCursors(
        accountDid: String,
        feedType: String,
        feedUri: String,
        boundarySince: String?,
        fillCursor: String?,
        pendingBoundary: String?,
    )

    @Query("DELETE FROM feed_remote_keys WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
