package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.CachedPostEntity
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

    /**
     * Every member of a whole page of items, in one query.
     *
     * The read path reads a page at a time, so asking per row made the member
     * lookup scale with page size — `pageSize + 1` queries to render one page,
     * on the path a 120 Hz scroll depends on. Ordered by position then ordinal
     * so the caller can group without re-sorting, and render order inside each
     * item is preserved.
     */
    @Query(
        "SELECT * FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position IN (:positions) ORDER BY position, ordinal",
    )
    suspend fun membersOfAll(
        accountDid: String,
        feedType: String,
        feedUri: String,
        positions: List<Int>,
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
     * The LEAF post of every item already rendered in this partition — the
     * posts that occupy a timeline slot, as opposed to the ones drawn only as a
     * cluster's context.
     *
     * Seeds the "one item per timeline post" pass across pagination. Distinct
     * from [renderedPostUris], and the distinction is load-bearing: a `Single`
     * whose post appears above only as some cluster's context is the
     * cluster-context pass's business, which keeps it when it is a repost.
     * Seeding this pass from every rendered post would drop it instead, and
     * silently change what a reposted reply looks like in the feed.
     */
    @Query(
        "SELECT leaf_uri FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND leaf_uri IS NOT NULL",
    )
    suspend fun renderedLeafUris(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): List<String>

    /**
     * Every item, in any partition of the account, whose leaf is [uri].
     *
     * Items only: a seam has no leaf. Returned whole so the caller can tell a
     * chain, which outlives one of its posts, from the variants that do not.
     */
    @Query(
        "SELECT * FROM feed_item " +
            "WHERE account_did = :accountDid AND leaf_uri = :uri AND row_type = 'item'",
    )
    suspend fun itemsWithLeaf(
        accountDid: String,
        uri: String,
    ): List<FeedItemEntity>

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

    /**
     * The lowest position at or above [from] held by a projected item, or
     * `null` if none is.
     *
     * The projection's half of a prepend's room check. `feed_post` and
     * `feed_item` are not packed alike: a local re-projection (D27) lays items
     * out contiguously from the partition's lowest entry, while catch-up
     * windows leave `feed_post` sparse, so a slot free in one can be taken in
     * the other — and the projection write is an upsert keyed on position, so
     * writing there replaces a row the reader can see.
     *
     * Seam rows are excluded: the seam marks exactly where the next fill page
     * goes, and is moved by every fill page that lands.
     */
    @Query(
        "SELECT MIN(position) FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position >= :from AND row_type != 'seam'",
    )
    suspend fun firstItemPositionAtOrAbove(
        accountDid: String,
        feedType: String,
        feedUri: String,
        from: Int,
    ): Int?

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

    /**
     * The position of the item [offset] places down from the newest, or `null`
     * when the projection holds fewer rows than that.
     *
     * The trim counts rows here too. Projection positions are as gapped as
     * entry positions — a prepend writes into the same reserved window — so a
     * cut-off derived from position arithmetic would keep an arbitrary fraction
     * of the cap.
     */
    @Query(
        "SELECT position FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "ORDER BY position LIMIT 1 OFFSET :offset",
    )
    suspend fun positionAtOffsetFromTop(
        accountDid: String,
        feedType: String,
        feedUri: String,
        offset: Int,
    ): Int?

    /**
     * Deletes the projection older than [position] — items and members
     * together.
     *
     * `@Transaction` for the same reason [clearPartition] is one: members left
     * without their item pin posts against reference-counted eviction, and an
     * item left without its members renders a card with no content.
     */
    @Transaction
    suspend fun deleteOlderThan(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    ) {
        deleteItemsOlderThan(accountDid, feedType, feedUri, position)
        deleteMembersOlderThan(accountDid, feedType, feedUri, position)
    }

    @Query(
        "DELETE FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position > :position",
    )
    suspend fun deleteItemsOlderThan(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    )

    @Query(
        "DELETE FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position > :position",
    )
    suspend fun deleteMembersOlderThan(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
    )

    /**
     * The leaf posts of the items sitting **above** [position], newest first.
     *
     * Backs the new-posts pill, which shows the authors of what arrived while
     * the reader was away. A `Flow`, because the writes it reports happen in
     * the background worker: the pill has to appear when the reader returns
     * without anything in the UI having asked.
     *
     * Items only — a seam marker has no leaf and is not something that
     * arrived. Limited at the query, since the pill shows at most a handful of
     * faces and the window above the anchor can be hundreds of rows.
     */
    @Query(
        "SELECT cp.* FROM feed_item AS fi " +
            "INNER JOIN cached_post AS cp " +
            "ON cp.account_did = fi.account_did AND cp.uri = fi.leaf_uri " +
            "WHERE fi.account_did = :accountDid AND fi.feed_type = :feedType AND fi.feed_uri = :feedUri " +
            "AND fi.row_type = 'item' AND fi.position < :position " +
            "ORDER BY fi.position LIMIT :limit",
    )
    fun leafPostsAbove(
        accountDid: String,
        feedType: String,
        feedUri: String,
        position: Int,
        limit: Int,
    ): Flow<List<CachedPostEntity>>

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

    /**
     * Removes member rows sitting under a seam row.
     *
     * A seam has no members. Ones found under it were left by a marker write
     * that replaced a projected item (before markers had a slot of their own,
     * nubecita-2xk0.1): the item's row became the seam, its members stayed.
     * Idempotent and partition-scoped, so it can run on every catch-up.
     */
    @Query(
        "DELETE FROM feed_item_member " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND position IN (SELECT position FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND row_type = 'seam')",
    )
    suspend fun deleteMembersUnderSeams(
        accountDid: String,
        feedType: String,
        feedUri: String,
    )

    /**
     * Removes the partition's **active** seam — the one marking a fill in
     * progress. Passive gap markers (`seam_exhausted = 1`) stay: each marks a
     * break a completed fill left, and removing one would leave the reader a
     * bare discontinuity (nubecita-2xk0).
     */
    @Query(
        "DELETE FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND row_type = 'seam' AND (seam_exhausted IS NULL OR seam_exhausted = 0)",
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
     * Positions of every seam marker in the partition, ascending — the active
     * seam and any passive gap markers alike.
     *
     * A re-projection splits the stored entries at these positions so that no
     * rebuilt item lands on a marker and no grouping reaches across one
     * (nubecita-2xk0.3).
     */
    @Query(
        "SELECT position FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND row_type = 'seam' ORDER BY position",
    )
    suspend fun seamPositions(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ): List<Int>

    @Query(
        "DELETE FROM feed_item " +
            "WHERE account_did = :accountDid AND feed_type = :feedType AND feed_uri = :feedUri " +
            "AND row_type = 'item'",
    )
    suspend fun clearItemRows(
        accountDid: String,
        feedType: String,
        feedUri: String,
    )

    /**
     * Clears a partition's projected items and their members, keeping its seam
     * markers.
     *
     * What a re-projection needs: the items are rebuilt, but a marker records a
     * break in the stored timeline that the projection cannot reconstruct, and
     * clearing it would leave the reader a bare discontinuity. Seams carry no
     * members, so every member row in the partition goes with the items.
     */
    @Transaction
    suspend fun clearProjectedItems(
        accountDid: String,
        feedType: String,
        feedUri: String,
    ) {
        clearItemRows(accountDid, feedType, feedUri)
        clearPartitionMembers(accountDid, feedType, feedUri)
    }

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
