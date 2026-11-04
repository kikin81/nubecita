package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One post's membership in one projected feed item, at a given render
 * [ordinal] — a cluster's root/parent/leaf, or a chain's posts oldest → newest.
 *
 * This is a junction table rather than a delimited `member_uris` column on
 * [FeedItemEntity], and the reason is eviction. A post must survive until
 * nothing references it, which means eviction has to *ask* whether any item
 * still uses a URI. Against a delimited column that question is a chain of
 * `LIKE` patterns with no index behind it — unanswerable in practice, so the
 * reference check would have quietly degraded into "delete anything with no
 * entry row", which is exactly how a cluster loses the root it renders.
 *
 * As rows, the same question is an indexed `NOT EXISTS`.
 *
 * [ordinal] is significant, not incidental: the first member determines the
 * item's stable list key (so a chain gaining a reply does not change its key
 * and strand the scroll anchor) and the last is the leaf, "the post in your
 * timeline".
 */
@Entity(
    tableName = "feed_item_member",
    primaryKeys = ["account_did", "feed_type", "feed_uri", "position", "ordinal"],
    indices = [Index("uri")],
)
data class FeedItemMemberEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_type") val feedType: String,
    @ColumnInfo(name = "feed_uri") val feedUri: String,
    val position: Int,
    val ordinal: Int,
    val uri: String,
)
