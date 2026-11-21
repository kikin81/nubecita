package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import net.kikin.nubecita.core.database.model.ThreadSnapshotEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotItemEntity
import kotlin.time.Instant

/**
 * DAO for post-detail thread snapshots: a `thread_snapshot` header row plus
 * its ordered `thread_snapshot_item` rows. The two tables always change
 * together, so every multi-table write is a `@Transaction`.
 *
 * Post bodies are not here — items name `cached_post` rows, and
 * [CachedPostDao.deleteUnreferenced] keeps any post a snapshot item points at.
 * Evicting snapshots first and then running that pass is what reclaims their
 * posts.
 */
@Dao
abstract class ThreadSnapshotDao {
    /** Replaces the snapshot for this focus: header upserted, items rewritten. */
    @Transaction
    open suspend fun replace(
        snapshot: ThreadSnapshotEntity,
        items: List<ThreadSnapshotItemEntity>,
    ) {
        deleteItems(snapshot.accountDid, snapshot.focusUri)
        upsertSnapshot(snapshot)
        insertItems(items)
    }

    @Query("SELECT * FROM thread_snapshot WHERE account_did = :accountDid AND focus_uri = :focusUri")
    abstract suspend fun snapshot(
        accountDid: String,
        focusUri: String,
    ): ThreadSnapshotEntity?

    @Query(
        "SELECT * FROM thread_snapshot_item " +
            "WHERE account_did = :accountDid AND focus_uri = :focusUri ORDER BY position",
    )
    abstract suspend fun items(
        accountDid: String,
        focusUri: String,
    ): List<ThreadSnapshotItemEntity>

    @Query("UPDATE thread_snapshot SET last_opened_at = :at WHERE account_did = :accountDid AND focus_uri = :focusUri")
    abstract suspend fun touch(
        accountDid: String,
        focusUri: String,
        at: Instant,
    )

    @Transaction
    open suspend fun delete(
        accountDid: String,
        focusUri: String,
    ) {
        deleteItems(accountDid, focusUri)
        deleteSnapshot(accountDid, focusUri)
    }

    /** Keeps the [cap] most recently opened snapshots for this account; drops the rest with their items. */
    @Transaction
    open suspend fun evictBeyond(
        accountDid: String,
        cap: Int,
    ) {
        deleteItemsBeyond(accountDid, cap)
        deleteSnapshotsBeyond(accountDid, cap)
    }

    @Transaction
    open suspend fun clearAccount(accountDid: String) {
        clearItems(accountDid)
        clearSnapshots(accountDid)
    }

    @Upsert
    protected abstract suspend fun upsertSnapshot(snapshot: ThreadSnapshotEntity)

    @Insert
    protected abstract suspend fun insertItems(items: List<ThreadSnapshotItemEntity>)

    @Query("DELETE FROM thread_snapshot_item WHERE account_did = :accountDid AND focus_uri = :focusUri")
    protected abstract suspend fun deleteItems(
        accountDid: String,
        focusUri: String,
    )

    @Query("DELETE FROM thread_snapshot WHERE account_did = :accountDid AND focus_uri = :focusUri")
    protected abstract suspend fun deleteSnapshot(
        accountDid: String,
        focusUri: String,
    )

    @Query(
        "DELETE FROM thread_snapshot_item WHERE account_did = :accountDid AND focus_uri NOT IN (" +
            "SELECT focus_uri FROM thread_snapshot WHERE account_did = :accountDid " +
            "ORDER BY last_opened_at DESC LIMIT :cap)",
    )
    protected abstract suspend fun deleteItemsBeyond(
        accountDid: String,
        cap: Int,
    )

    @Query(
        "DELETE FROM thread_snapshot WHERE account_did = :accountDid AND focus_uri NOT IN (" +
            "SELECT focus_uri FROM thread_snapshot WHERE account_did = :accountDid " +
            "ORDER BY last_opened_at DESC LIMIT :cap)",
    )
    protected abstract suspend fun deleteSnapshotsBeyond(
        accountDid: String,
        cap: Int,
    )

    @Query("DELETE FROM thread_snapshot_item WHERE account_did = :accountDid")
    protected abstract suspend fun clearItems(accountDid: String)

    @Query("DELETE FROM thread_snapshot WHERE account_did = :accountDid")
    protected abstract suspend fun clearSnapshots(accountDid: String)
}
