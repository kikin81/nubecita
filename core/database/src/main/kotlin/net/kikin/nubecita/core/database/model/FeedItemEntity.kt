package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One **rendered row** of a cached feed partition: the persisted grouping
 * projection.
 *
 * [FeedPostEntity] is one row per fetched *entry*; this is one row per item the
 * reader actually sees, which is not the same count. Several entries collapse
 * into one `ReplyCluster` or `SelfThreadChain`, de-duplication drops others
 * entirely, and a seam is a row with no entry behind it at all. Keeping the two
 * grains apart is what lets the grouping be computed once at write time and
 * read back without re-deriving it.
 *
 * It stores **structure, not content**. Its members live in
 * [FeedItemMemberEntity] and reference [CachedPostEntity] rows, so a post that
 * is both its own item and another item's context is stored once. See
 * [CachedPostEntity] for why hydrating from a feed partition instead is not
 * possible, and [FeedItemMemberEntity] for why membership is rows rather than a
 * delimited column.
 *
 * [rowType] separates a real item from a **seam marker**. A seam cannot be
 * inferred from a gap in [position]: gaps are expected (a bounded prepend
 * reserves a window), and a keyset `PagingSource` walks from one existing row
 * to the next, so it would step straight over the gap and render nothing. A
 * marker row gives the seam a position, a stable key, and somewhere to keep
 * [seamExhausted] across process death.
 *
 * [itemKey] is the stable list key, persisted rather than re-derived so the
 * read path and the write path cannot disagree about it. It is namespaced per
 * variant (`chain:`/`cluster:` prefixes): a chain rooted at post P keyed on a
 * bare P would collide with a `Single` rendering P, and a duplicate key in a
 * `LazyColumn` is a crash rather than a glitch.
 *
 * [leafUri] is "the post in your timeline" — the highest-ordinal member,
 * denormalized here so the interaction overlay's deletion check does not need a
 * subquery on a hot path. It is deliberately *not* what [itemKey] is derived
 * from: the key comes from the **first** member, so a chain gaining a reply
 * keeps its key instead of stranding the scroll anchor.
 */
@Entity(
    tableName = "feed_item",
    primaryKeys = ["account_did", "feed_type", "feed_uri", "position"],
    indices = [Index("item_key"), Index("leaf_uri")],
)
data class FeedItemEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_type") val feedType: String,
    @ColumnInfo(name = "feed_uri") val feedUri: String,
    val position: Int,
    /** [ROW_TYPE_ITEM] or [ROW_TYPE_SEAM]. */
    @ColumnInfo(name = "row_type") val rowType: String,
    /** `single` / `cluster` / `chain` / `blocked` / `notfound`; null for a seam. */
    val variant: String? = null,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "leaf_uri") val leafUri: String? = null,
    @ColumnInfo(name = "suppressed_reply_count") val suppressedReplyCount: Int = 0,
    /** Seam rows only: whether the catch-up range above this seam is exhausted. */
    @ColumnInfo(name = "seam_exhausted") val seamExhausted: Boolean? = null,
) {
    companion object {
        const val ROW_TYPE_ITEM = "item"
        const val ROW_TYPE_SEAM = "seam"
    }
}
