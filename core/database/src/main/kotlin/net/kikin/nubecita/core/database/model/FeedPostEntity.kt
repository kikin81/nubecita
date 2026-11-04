package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant

/**
 * A per-position denormalized snapshot of a single feed **entry** inside one
 * cached feed partition. Rows are partitioned by `(account_did, feed_type,
 * feed_uri)` and ordered within a partition by [position], where **ascending
 * position is newest-first**: the RemoteMediator writes position 0 at the top
 * of the newest (REFRESH) page and assigns *higher* positions to *older* posts
 * loaded on later APPEND pages, so `ORDER BY position` yields newest→oldest.
 *
 * [position] is signed, and negative values are expected: a PREPEND writes
 * newer entries *above* the existing rows without renumbering them, so a
 * partition that has caught up holds negatives at its head. `ORDER BY position`
 * is unaffected; see the `core-feed-cache` spec for the allocation rules.
 *
 * [feedType] stores the `FeedType` enum NAME as a `String`; the enum itself
 * lives in `:core:feed-cache` (the entity must not depend on that module).
 * [feedUri] holds the generator/list AT-URI for DISCOVER/CUSTOM/LIST and an
 * **empty-string sentinel** for FOLLOWING.
 *
 * [entryBlob] holds the entire wire `app.bsky.feed.defs#feedViewPost` — the
 * post **together with** its per-entry `reason` and `reply` context —
 * serialized to a JSON `String` by the `:core:feed-cache` write-through mapper,
 * which re-maps it back on read. The queryable columns above are denormalized
 * projections of that blob for indexed lookups. It is a plain nullable text
 * column here — no `TypeConverter` is needed.
 *
 * Storing the *entry* rather than the bare `postView` is load-bearing rather
 * than incidental. `FeedItemUi.ReplyCluster` and `FeedItemUi.SelfThreadChain`
 * are projected from the per-entry `reason` and `reply` fields, so a partition
 * holding only posts cannot reconstruct the app's feed — it can only serve a
 * flat list, which is why the widget was the cache's one consumer until now.
 *
 * [repostedByDid], [replyRootUri] and [replyParentUri] denormalize the three
 * pieces of that entry-level context the projection reads most: whether the
 * entry is a repost (reposts are exempt from thread-root de-duplication) and
 * the thread it belongs to. They carry no `@Index` on purpose — nothing queries
 * them yet, and the grouping projection runs over a whole fetched page in
 * memory. Add an index when a query demands one, not in anticipation.
 *
 * `@Index` on [uri] and [authorDid] serve by-uri (deep-link /
 * `:core:post-interactions` overlay / scroll-anchor resolution) and by-author
 * lookups that the composite primary key does not cover. The entity never
 * crosses the `:core:feed-cache` boundary; no `asExternalModel()` lives here
 * (it needs embed deserialization).
 */
@Entity(
    tableName = "feed_post",
    primaryKeys = ["account_did", "feed_type", "feed_uri", "position"],
    indices = [Index("uri"), Index("author_did")],
)
data class FeedPostEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_type") val feedType: String,
    @ColumnInfo(name = "feed_uri") val feedUri: String,
    val position: Int,
    val uri: String,
    val cid: String,
    @ColumnInfo(name = "author_did") val authorDid: String,
    @ColumnInfo(name = "indexed_at") val indexedAt: Instant,
    val text: String,
    /** The reposting account's DID when this entry carried a `ReasonRepost`, else null. */
    @ColumnInfo(name = "reposted_by_did") val repostedByDid: String? = null,
    /** The entry's `reply.root` URI when the root is a readable post, else null. */
    @ColumnInfo(name = "reply_root_uri") val replyRootUri: String? = null,
    /** The entry's `reply.parent` URI when the parent is a readable post, else null. */
    @ColumnInfo(name = "reply_parent_uri") val replyParentUri: String? = null,
    @ColumnInfo(name = "entry_blob") val entryBlob: String?,
)
