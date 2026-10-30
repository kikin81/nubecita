package net.kikin.nubecita.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchPostActionDao {
    @Query("SELECT * FROM watch_pending_post WHERE account_did = :accountDid ORDER BY created_at_ms")
    fun observe(accountDid: String): Flow<List<WatchPendingPostEntity>>

    @Query("SELECT * FROM watch_pending_post WHERE action_id = :actionId")
    suspend fun get(actionId: String): WatchPendingPostEntity?

    @Insert
    suspend fun insert(post: WatchPendingPostEntity)

    @Query("UPDATE watch_pending_post SET status = 'failed' WHERE action_id = :actionId")
    suspend fun markFailed(actionId: String)

    @Query("DELETE FROM watch_pending_post WHERE action_id IN (:actionIds)")
    suspend fun delete(actionIds: List<String>)

    /** Sign-out or account switch: drops every other account's posts. */
    @Query("DELETE FROM watch_pending_post WHERE account_did != :accountDid")
    suspend fun deleteExcept(accountDid: String)
}
