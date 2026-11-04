package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity

/**
 * DAO for the `feed_item` table — the persisted grouping projection, one row
 * per rendered item, ordered by `position` ascending (newest-first, matching
 * the entry partition).
 *
 * This DAO deliberately exposes **no** `PagingSource`. The feed pages over the
 * projection with a hand-written keyset source over `position`, because Room's
 * generated offset-based source resolves its refresh key to a different row
 * once any write invalidates the table — the viewport jump this change exists
 * to prevent. That source lands with the screen migration.
 */
@Dao
interface FeedItemDao {
    @Upsert
    suspend fun upsert(items: List<FeedItemEntity>)

    @Upsert
    suspend fun upsertMembers(members: List<FeedItemMemberEntity>)

    /** One item's members in render order. */
    @Query(
        "SELECT * FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position = :position ORDER BY ordinal",
    )
    suspend fun membersOf(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    ): List<FeedItemMemberEntity>

    @Query(
        "SELECT * FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "ORDER BY position",
    )
    suspend fun itemsIn(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): List<FeedItemEntity>

    /**
     * The window `[fromPosition, …)` in order, for the keyset read path.
     *
     * Keyset rather than offset on purpose: positions are stable, so a reload
     * after an invalidating write resolves to the same row, where an offset
     * would have shifted by however many rows were prepended.
     */
    @Query(
        "SELECT * FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position >= :fromPosition " +
            "ORDER BY position LIMIT :limit",
    )
    suspend fun itemsFrom(
        accountDid: String,
        feedType: String,
        feedUri: String,
        fromPosition: Int,
        limit: Int,
    ): List<FeedItemEntity>

    /**
     * The thread roots already rendered in this partition — the de-duplication
     * seed for an APPEND.
     *
     * An APPEND lands below everything here, so a page reusing one of these
     * roots is older than what the reader can already see and is dropped. A
     * PREPEND deliberately does **not** call this: entries above the reader's
     * position are a new event (design D7).
     */
    @Query(
        "SELECT DISTINCT thread_root_uri FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND thread_root_uri IS NOT NULL",
    )
    suspend fun seenThreadRoots(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): List<String>

    /**
     * Every post this partition already puts on screen.
     *
     * Feeds `visibleFromAbove`: a suppressed post the reader can already see is
     * not "more replies", and without this the count would inflate across page
     * boundaries.
     */
    @Query(
        "SELECT DISTINCT uri FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun renderedPostUris(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): List<String>

    /**
     * The partition's last item — the one a newly appended page may extend.
     *
     * A self-author chain can straddle a pagination cut, and the merge needs
     * the existing tail to prepend to, so this is read before an append writes.
     */
    @Query(
        "SELECT * FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND row_type = 'item' ORDER BY position DESC LIMIT 1",
    )
    suspend fun tailItem(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): FeedItemEntity?

    /**
     * The window immediately **above** [beforePosition], newest-last.
     *
     * The upward half of the keyset read. A reader restored at an anchor sits
     * mid-partition and must be able to scroll back towards newer items, which
     * a forward-only source cannot serve. Returned in descending order; the
     * caller reverses it to display order.
     */
    @Query(
        "SELECT * FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position < :beforePosition " +
            "ORDER BY position DESC LIMIT :limit",
    )
    suspend fun itemsBefore(
        accountDid: String,
        feedType: String,
        feedUri: String,
        beforePosition: Int,
        limit: Int,
    ): List<FeedItemEntity>

    /** Resolves a persisted scroll anchor: the position of the row with this key. */
    @Query(
        "SELECT position FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND item_key = :itemKey",
    )
    suspend fun positionOf(
        accountDid: String,
        feedType: String,
        feedUri: String,
        itemKey: String,
    ): Int?

    /**
     * The nearest surviving row at or below [fromPosition].
     *
     * This is the dead-anchor fallback: when the anchored item is gone —
     * deleted, moderated out, evicted — restore resolves *downwards*, because
     * the items just under where the reader stopped are the ones they had not
     * read yet.
     */
    @Query(
        "SELECT * FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position >= :fromPosition " +
            "ORDER BY position LIMIT 1",
    )
    suspend fun firstAtOrBelow(
        accountDid: String,
        feedType: String,
        feedUri: String,
        fromPosition: Int,
    ): FeedItemEntity?

    @Query(
        "SELECT MIN(position) FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun minPosition(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): Int?

    @Query(
        "SELECT MAX(position) FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun maxPosition(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): Int?

    /** The partition's seam marker, if a catch-up left one. At most one exists. */
    @Query(
        "SELECT * FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND row_type = 'seam' ORDER BY position LIMIT 1",
    )
    suspend fun seam(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): FeedItemEntity?

    /**
     * Removes one item and its members.
     *
     * Used when an appended page absorbs the partition's tail into a chain:
     * the old row has to go, or the same posts render twice — once as the tail
     * and once inside the chain that swallowed it.
     */
    @Transaction
    suspend fun deleteItemAt(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    ) {
        deleteItemRowAt(accountDid, feedType, feedUri, position)
        deleteMembersAt(accountDid, feedType, feedUri, position)
    }

    @Query(
        "DELETE FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position = :position",
    )
    suspend fun deleteItemRowAt(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    )

    @Query(
        "DELETE FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position = :position",
    )
    suspend fun deleteMembersAt(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    )

    @Query(
        "DELETE FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND row_type = 'seam'",
    )
    suspend fun clearSeam(
        accountDid: String,
        feedType: String,
        feedUri: String,
    )

    @Query(
        "DELETE FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun clearPartitionItems(
        accountDid: String,
        feedType: String,
        feedUri: String,
    )

    @Query(
        "DELETE FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri",
    )
    suspend fun clearPartitionMembers(
        accountDid: String,
        feedType: String,
        feedUri: String,
    )

    /**
     * Clears a partition's projection, items and members together.
     *
     * `@Transaction` because a half-cleared projection is a worse state than
     * either end of it: members without items leak rows that pin posts against
     * eviction, and items without members render a card with no content.
     */
    @Transaction
    suspend fun clearPartition(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ) {
        clearPartitionItems(accountDid, feedType, feedUri)
        clearPartitionMembers(accountDid, feedType, feedUri)
    }

    @Query("DELETE FROM feed_item WHERE account_did = :accountDid")
    suspend fun clearAccountItems(accountDid: String)

    @Query("DELETE FROM feed_item_member WHERE account_did = :accountDid")
    suspend fun clearAccountMembers(accountDid: String)

    @Transaction
    suspend fun clearAccount(accountDid: String) {
        clearAccountItems(accountDid)
        clearAccountMembers(accountDid)
    }
}
