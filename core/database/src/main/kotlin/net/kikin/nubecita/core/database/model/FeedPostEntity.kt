package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One feed **entry** inside one cached feed partition: a position, a reference
 * to the post it shows, and the entry-level context that belongs to the
 * timeline rather than to the post.
 *
 * Rows are partitioned by `(account_did, feed_type, feed_uri)` and ordered
 * within a partition by [position], where **ascending position is
 * newest-first**: position 0 is the top of the newest page and higher positions
 * are older posts from later APPEND pages, so `ORDER BY position` yields
 * newest→oldest.
 *
 * [position] is signed, and negative values are expected: a PREPEND writes
 * newer entries *above* the existing rows without renumbering them, so a
 * partition that has caught up holds negatives at its head.
 *
 * This row carries **no post content**. [uri] references [CachedPostEntity]
 * `(account_did, uri)`, which is the single store for the post itself. The same
 * post is frequently both its own timeline entry and another entry's reply
 * context, and a copy per grain would be two serialized blobs that drift apart
 * on a profile, label or content change.
 *
 * [feedType] stores the `FeedType` enum NAME as a `String`; the enum itself
 * lives in `:core:feed-cache` (the entity must not depend on that module).
 * [feedUri] holds the generator/list AT-URI for DISCOVER/CUSTOM/LIST and an
 * **empty-string sentinel** for FOLLOWING.
 *
 * The remaining columns are the per-entry context, which is **not** a property
 * of the post and so has nowhere else to live: the same post reposted by two
 * different people is two entries, and a post's own row cannot say which. Each
 * is nullable because a plain entry has none of them.
 *
 * [repostedByLabel] is the rendered attribution (`displayName ?: handle`),
 * stored rather than derived so the read path needs no second lookup for the
 * reposting account's profile. [repostedByDid] is the queryable identity.
 *
 * `@Index` on [uri] serves the join to [CachedPostEntity] and the by-uri
 * lookups (deep link, interaction overlay, scroll-anchor resolution). The
 * by-author index now lives on [CachedPostEntity], where the author is.
 */
@Entity(
    tableName = "feed_post",
    primaryKeys = ["account_did", "feed_type", "feed_uri", "position"],
    indices = [Index("uri")],
)
data class FeedPostEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_type") val feedType: String,
    @ColumnInfo(name = "feed_uri") val feedUri: String,
    val position: Int,
    val uri: String,
    @ColumnInfo(name = "reposted_by_did") val repostedByDid: String? = null,
    @ColumnInfo(name = "reposted_by_label") val repostedByLabel: String? = null,
    @ColumnInfo(name = "reply_root_uri") val replyRootUri: String? = null,
    @ColumnInfo(name = "reply_parent_uri") val replyParentUri: String? = null,
    /**
     * The entry's own wire fields — `reason` and `reply` — as a JSON object,
     * or null when it carries neither.
     *
     * This is not a duplicate of anything: the post lives once in
     * [CachedPostEntity], and these two fields have no other home. The three
     * columns above are *denormalizations* of this blob for querying, not
     * replacements for it — they reduce `reason` to a DID and a label and
     * `reply` to two URIs, which is enough to query and not enough to
     * reconstruct.
     *
     * It exists so the grouping projection can be recomputed from disk. The
     * projection's input is a whole wire entry with these as open unions, so
     * without them a preference change could only be applied by refetching —
     * which costs the reader the position this cache exists to keep.
     */
    @ColumnInfo(name = "entry_context_blob") val entryContextBlob: String? = null,
)
