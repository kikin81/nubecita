package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlinx.datetime.Instant

/**
 * A per-position denormalized snapshot of a single post inside one cached
 * feed partition. Rows are partitioned by `(account_did, feed_type,
 * feed_uri)` and ordered within a partition by [position], where **ascending
 * position is newest-first**: the RemoteMediator writes position 0 at the top
 * of the newest (REFRESH) page and assigns *higher* positions to *older* posts
 * loaded on later APPEND pages, so `ORDER BY position` yields newest→oldest.
 *
 * [feedType] stores the `FeedType` enum NAME as a `String`; the enum itself
 * lives in `:core:feed-cache` (the entity must not depend on that module).
 * [feedUri] holds the generator/list AT-URI for DISCOVER/CUSTOM/LIST and an
 * **empty-string sentinel** for FOLLOWING.
 *
 * [postBlob] holds the entire wire `app.bsky.feed.defs#postView` serialized to
 * a JSON `String` by the `:core:feed-cache` write-through mapper; the cache
 * re-maps it back to `PostUi` on read (the queryable columns above are
 * denormalized projections of this blob for indexed lookups). It is a plain
 * nullable text column here — no `TypeConverter` is needed.
 *
 * `@Index` on [uri] and [authorDid] serve by-uri (deep-link /
 * `:core:post-interactions` overlay) and by-author lookups that the composite
 * primary key does not cover. The entity never crosses the `:core:feed-cache`
 * boundary; no `asExternalModel()` lives here (it needs embed deserialization,
 * added in a later PR).
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
    @ColumnInfo(name = "post_blob") val postBlob: String?,
)
