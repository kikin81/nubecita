package net.kikin.nubecita.wear.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A synced feed: one row per (account, feed). [publishedAtMs] is the phone's
 * timestamp for the snapshot, used to ignore an older snapshot that arrives
 * after a newer one.
 */
@Entity(tableName = "watch_feed", primaryKeys = ["account_did", "feed_key"])
data class WatchFeedEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_key") val feedKey: String,
    @ColumnInfo(name = "feed_name") val feedName: String?,
    @ColumnInfo(name = "published_at_ms") val publishedAtMs: Long,
)

/**
 * One post of a synced feed, in feed order ([position] 0 = newest). Images are
 * local file paths written at ingest, so the UI never touches the Data Layer.
 */
@Entity(tableName = "watch_post", primaryKeys = ["account_did", "feed_key", "position"])
data class WatchPostEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_key") val feedKey: String,
    val position: Int,
    val uri: String,
    val cid: String,
    @ColumnInfo(name = "author_did") val authorDid: String,
    @ColumnInfo(name = "author_handle") val authorHandle: String,
    @ColumnInfo(name = "author_display_name") val authorDisplayName: String?,
    @ColumnInfo(name = "author_avatar_path") val authorAvatarPath: String?,
    val text: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "reply_count") val replyCount: Int,
    @ColumnInfo(name = "repost_count") val repostCount: Int,
    @ColumnInfo(name = "like_count") val likeCount: Int,
    @ColumnInfo(name = "reposted_by") val repostedBy: String?,
    @ColumnInfo(name = "like_uri") val likeUri: String?,
    @ColumnInfo(name = "repost_uri") val repostUri: String? = null,
    val bookmarked: Boolean,
    @ColumnInfo(name = "image_count") val imageCount: Int,
    @ColumnInfo(name = "is_video") val isVideo: Boolean,
    @ColumnInfo(name = "has_content_warning") val hasContentWarning: Boolean,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
)

/**
 * A like, repost or bookmark (or an undo) the user made on the watch, kept
 * until the phone's answer and then a snapshot showing it have both arrived.
 * Overlaid on the synced post so the change shows immediately
 * ([effectiveViewer]).
 *
 * [kind] is the action's wire name ([PostActionKind.wire]). [confirmed] is set
 * by an ok result, with the record a like or repost created in [recordUri];
 * a failed result deletes the row, which silently reverts the post.
 */
@Entity(tableName = "watch_pending_action")
data class WatchPendingActionEntity(
    @PrimaryKey @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "post_uri") val postUri: String,
    val kind: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    val confirmed: Boolean = false,
    @ColumnInfo(name = "record_uri") val recordUri: String? = null,
    @ColumnInfo(name = "completed_at_ms") val completedAtMs: Long? = null,
)

/**
 * The account's DM snapshot header: when the phone published it and the
 * unread total. Its presence means the phone has synced DMs at least once.
 */
@Entity(tableName = "watch_dm_state")
data class WatchDmStateEntity(
    @PrimaryKey @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "published_at_ms") val publishedAtMs: Long,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
)

/** A synced conversation, in the phone's order ([position] 0 = most recent). */
@Entity(tableName = "watch_convo", primaryKeys = ["account_did", "convo_id"])
data class WatchConvoEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "convo_id") val convoId: String,
    val position: Int,
    val title: String,
    @ColumnInfo(name = "is_group") val isGroup: Boolean,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    val muted: Boolean,
    @ColumnInfo(name = "last_message_at_ms") val lastMessageAtMs: Long?,
)

/** A synced message, oldest first within its conversation ([position] 0 = oldest). */
@Entity(tableName = "watch_message", primaryKeys = ["account_did", "convo_id", "position"])
data class WatchMessageEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "convo_id") val convoId: String,
    val position: Int,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "from_viewer") val fromViewer: Boolean,
    @ColumnInfo(name = "sender_name") val senderName: String?,
    val text: String,
    @ColumnInfo(name = "sent_at_ms") val sentAtMs: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "has_attachment") val hasAttachment: Boolean,
    /** The message's reactions as contract JSON ([reactionList] reads them). */
    val reactions: String = "[]",
)

/**
 * A quick reply or reaction sent from the watch (W4), kept until a DM
 * snapshot shows it. [kind] is `reply` or `react`; [text] is the reply's text
 * or the reaction's emoji; [targetMessageId] is the reacted-to message.
 * [status] is `sending`, then `sent` (with the reply's [sentMessageId]) or
 * `failed` (the thread offers a retry).
 */
@Entity(tableName = "watch_pending_dm")
data class WatchPendingDmEntity(
    @PrimaryKey @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "convo_id") val convoId: String,
    val kind: String,
    val text: String,
    @ColumnInfo(name = "target_message_id") val targetMessageId: String? = null,
    @ColumnInfo(name = "sent_message_id") val sentMessageId: String? = null,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    val status: String = STATUS_SENDING,
) {
    companion object {
        const val KIND_REPLY = "reply"
        const val KIND_REACT = "react"
        const val STATUS_SENDING = "sending"
        const val STATUS_SENT = "sent"
        const val STATUS_FAILED = "failed"
    }
}
