package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant

/**
 * One cached post per account, keyed by its AT-URI.
 *
 * This is the single store for post content. Both grains that order posts
 * reference it rather than carrying their own serialized copy: [FeedPostEntity]
 * rows (a feed partition's entries, in position order) and the grouped
 * projection rows. Without it the same post is serialized twice whenever it
 * appears both as a standalone timeline item and as another item's reply
 * context, and the two copies can then diverge on a profile, label or content
 * change.
 *
 * Hydrating from a feed partition instead is not possible, which is the reason
 * this table exists rather than a join back to [FeedPostEntity]: a reply's root
 * and parent usually are **not** timeline entries, so they hold no position in
 * any partition. They arrive inside an entry's `reply` field and are stored
 * here, which is also what lets a context post outlive the entry that
 * introduced it.
 *
 * [postBlob] holds the wire `app.bsky.feed.defs#postView`. The columns above it
 * are denormalized projections for lookups that should not deserialize.
 *
 * [unavailable] marks a post the viewer cannot read — a `blockedPost` or
 * `notFoundPost` reply context. Such a row has a URI and nothing else: no blob,
 * no author, no text. It still has to exist, because "this thread had a parent
 * and you cannot see it" is information the projection renders as a tombstone,
 * and dropping the row would silently turn a blocked reply into a root post.
 *
 * Eviction is reference-aware rather than partition-scoped: a row survives
 * until no live partition row and no live projection row points at its URI. A
 * context post has no position of its own, so position-based trimming cannot
 * see it.
 */
@Entity(
    tableName = "cached_post",
    primaryKeys = ["account_did", "uri"],
    indices = [Index("author_did")],
)
data class CachedPostEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    val uri: String,
    val cid: String? = null,
    @ColumnInfo(name = "author_did") val authorDid: String? = null,
    @ColumnInfo(name = "indexed_at") val indexedAt: Instant? = null,
    val text: String? = null,
    @ColumnInfo(name = "post_blob") val postBlob: String? = null,
    /** `"blocked"` or `"notfound"` for an unreadable context post; null when readable. */
    val unavailable: String? = null,
)
