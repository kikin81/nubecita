package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * One feed entry joined to the post it references — the read shape of a cached
 * partition, and what the feed's `PagingSource` emits.
 *
 * [FeedPostEntity] holds the entry envelope and [CachedPostEntity] holds the
 * post, so neither alone can be rendered: the entry knows the position and the
 * repost attribution, the post knows the author and the content. This pairs
 * them without reintroducing a duplicate copy of the post.
 *
 * [post] is non-null because the join is an inner one. A partition row whose
 * post is missing from the store is therefore absent from the read rather than
 * surfacing as a half-row — a state that should not occur (both are written in
 * one transaction) and that would otherwise have to be rendered as something.
 */
data class CachedFeedEntry(
    val position: Int,
    val uri: String,
    @ColumnInfo(name = "reposted_by_did") val repostedByDid: String?,
    @ColumnInfo(name = "reposted_by_label") val repostedByLabel: String?,
    @ColumnInfo(name = "reply_root_uri") val replyRootUri: String?,
    @ColumnInfo(name = "reply_parent_uri") val replyParentUri: String?,
    @Embedded(prefix = "p_") val post: CachedPostEntity,
)
