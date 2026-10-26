package net.kikin.nubecita.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDmDao {
    @Query("SELECT * FROM watch_dm_state WHERE account_did = :accountDid")
    fun observeState(accountDid: String): Flow<WatchDmStateEntity?>

    @Query("SELECT * FROM watch_convo WHERE account_did = :accountDid ORDER BY position")
    fun observeConvos(accountDid: String): Flow<List<WatchConvoEntity>>

    @Query("SELECT * FROM watch_message WHERE account_did = :accountDid AND convo_id = :convoId ORDER BY position")
    fun observeMessages(
        accountDid: String,
        convoId: String,
    ): Flow<List<WatchMessageEntity>>

    /** The last message of each conversation, for the list's preview line. */
    @Query(
        "SELECT m.* FROM watch_message m WHERE m.account_did = :accountDid AND m.position = " +
            "(SELECT MAX(position) FROM watch_message WHERE account_did = m.account_did AND convo_id = m.convo_id)",
    )
    fun observeLastMessages(accountDid: String): Flow<List<WatchMessageEntity>>

    @Query("SELECT published_at_ms FROM watch_dm_state WHERE account_did = :accountDid")
    suspend fun publishedAtMs(accountDid: String): Long?

    /** Replaces the account's DMs in one transaction, so the UI never sees half a snapshot. */
    @Transaction
    suspend fun replaceDms(
        state: WatchDmStateEntity,
        convos: List<WatchConvoEntity>,
        messages: List<WatchMessageEntity>,
    ) {
        deleteDms(state.accountDid)
        upsertState(state)
        insertConvos(convos)
        insertMessages(messages)
    }

    /** The phone deleted the snapshot, or the account signed out. */
    @Transaction
    suspend fun deleteDms(accountDid: String) {
        deleteMessages(accountDid)
        deleteConvos(accountDid)
        deleteState(accountDid)
    }

    /** Drops every account but [accountDid] (empty drops all). */
    @Transaction
    suspend fun keepOnlyAccount(accountDid: String) {
        deleteMessagesExcept(accountDid)
        deleteConvosExcept(accountDid)
        deleteStateExcept(accountDid)
    }

    @Upsert
    suspend fun upsertState(state: WatchDmStateEntity)

    @Insert
    suspend fun insertConvos(convos: List<WatchConvoEntity>)

    @Insert
    suspend fun insertMessages(messages: List<WatchMessageEntity>)

    @Query("DELETE FROM watch_message WHERE account_did = :accountDid")
    suspend fun deleteMessages(accountDid: String)

    @Query("DELETE FROM watch_convo WHERE account_did = :accountDid")
    suspend fun deleteConvos(accountDid: String)

    @Query("DELETE FROM watch_dm_state WHERE account_did = :accountDid")
    suspend fun deleteState(accountDid: String)

    @Query("DELETE FROM watch_message WHERE account_did != :accountDid")
    suspend fun deleteMessagesExcept(accountDid: String)

    @Query("DELETE FROM watch_convo WHERE account_did != :accountDid")
    suspend fun deleteConvosExcept(accountDid: String)

    @Query("DELETE FROM watch_dm_state WHERE account_did != :accountDid")
    suspend fun deleteStateExcept(accountDid: String)
}
