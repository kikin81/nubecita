package net.kikin.nubecita.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDmActionDao {
    @Query("SELECT * FROM watch_pending_dm WHERE account_did = :accountDid ORDER BY created_at_ms")
    fun observe(accountDid: String): Flow<List<WatchPendingDmEntity>>

    @Query("SELECT * FROM watch_pending_dm WHERE account_did = :accountDid ORDER BY created_at_ms")
    suspend fun all(accountDid: String): List<WatchPendingDmEntity>

    @Query("SELECT * FROM watch_pending_dm WHERE action_id = :actionId")
    suspend fun get(actionId: String): WatchPendingDmEntity?

    @Insert
    suspend fun insert(action: WatchPendingDmEntity)

    @Query("UPDATE watch_pending_dm SET status = 'sent', sent_message_id = :sentMessageId WHERE action_id = :actionId")
    suspend fun markSent(
        actionId: String,
        sentMessageId: String?,
    )

    @Query("UPDATE watch_pending_dm SET status = 'failed' WHERE action_id = :actionId")
    suspend fun markFailed(actionId: String)

    @Query("DELETE FROM watch_pending_dm WHERE action_id IN (:actionIds)")
    suspend fun delete(actionIds: List<String>)

    /** Sign-out or account switch: drops every other account's replies and reactions. */
    @Query("DELETE FROM watch_pending_dm WHERE account_did != :accountDid")
    suspend fun deleteExcept(accountDid: String)
}
