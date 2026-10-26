package net.kikin.nubecita.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchActionDao {
    @Query("SELECT * FROM watch_pending_action WHERE account_did = :accountDid ORDER BY created_at_ms")
    fun observe(accountDid: String): Flow<List<WatchPendingActionEntity>>

    @Query("SELECT * FROM watch_pending_action WHERE account_did = :accountDid ORDER BY created_at_ms")
    suspend fun all(accountDid: String): List<WatchPendingActionEntity>

    @Insert
    suspend fun insert(action: WatchPendingActionEntity)

    @Query("UPDATE watch_pending_action SET confirmed = 1, record_uri = :recordUri, completed_at_ms = :completedAtMs WHERE action_id = :actionId")
    suspend fun markConfirmed(
        actionId: String,
        recordUri: String?,
        completedAtMs: Long,
    )

    @Query("DELETE FROM watch_pending_action WHERE action_id IN (:actionIds)")
    suspend fun delete(actionIds: List<String>)

    /** Sign-out or account switch: drops every other account's actions. */
    @Query("DELETE FROM watch_pending_action WHERE account_did != :accountDid")
    suspend fun deleteExcept(accountDid: String)
}
