package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant

/**
 * The last-seen shape of one post-detail thread, keyed by its focus post.
 *
 * A snapshot is an ordered list of [ThreadSnapshotItemEntity] rows — the
 * thread already flattened (ancestors, focus, replies depth-first) but NOT
 * moderated: post bodies live in `cached_post` and are re-mapped with the
 * viewer's current moderation preferences on every read, so a cached thread
 * never shows something the viewer has since hidden.
 *
 * [lastOpenedAt] orders the per-account cap.
 */
@Entity(
    tableName = "thread_snapshot",
    primaryKeys = ["account_did", "focus_uri"],
)
data class ThreadSnapshotEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "focus_uri") val focusUri: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Instant,
)

/**
 * One position in a [ThreadSnapshotEntity]. [kind] is a [ThreadSnapshotKind]
 * constant; [postUri] names the `cached_post` row holding the body (a
 * blocked / not-found slot has no body, only its URI, and the other-replies
 * marker borrows the focus URI). [depth] is the reply
 * nesting level (0 for ancestors and the focus). [authorDid] is set only for
 * a blocked slot, which renders its author.
 */
@Entity(
    tableName = "thread_snapshot_item",
    primaryKeys = ["account_did", "focus_uri", "position"],
    indices = [Index(value = ["account_did", "post_uri"])],
)
data class ThreadSnapshotItemEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "focus_uri") val focusUri: String,
    val position: Int,
    val kind: String,
    @ColumnInfo(name = "post_uri") val postUri: String,
    val depth: Int,
    @ColumnInfo(name = "author_did") val authorDid: String?,
    /**
     * What the ordered thread read adds to a position, all null on a row
     * written before it existed (or by the older read): the server's 1-based
     * place of this post in its author's thread and that thread's length, how
     * many of its replies the response left out, and whether its parent chain
     * was cut above it.
     */
    @ColumnInfo(name = "op_thread_index") val opThreadIndex: Int? = null,
    @ColumnInfo(name = "op_thread_count") val opThreadCount: Int? = null,
    @ColumnInfo(name = "more_replies") val moreReplies: Int? = null,
    @ColumnInfo(name = "more_parents") val moreParents: Boolean? = null,
)

/** Values of [ThreadSnapshotItemEntity.kind]. */
object ThreadSnapshotKind {
    const val ANCESTOR = "ancestor"
    const val FOCUS = "focus"
    const val REPLY = "reply"
    const val BLOCKED = "blocked"
    const val NOT_FOUND = "notfound"

    /**
     * A body-less trailing row: the server holds replies it left out of this
     * thread (muted, or hidden by threadgate) and they can be asked for.
     */
    const val OTHER_REPLIES = "other_replies"
}
