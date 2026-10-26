package net.kikin.nubecita.wear.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

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
    val bookmarked: Boolean,
    @ColumnInfo(name = "image_count") val imageCount: Int,
    @ColumnInfo(name = "is_video") val isVideo: Boolean,
    @ColumnInfo(name = "has_content_warning") val hasContentWarning: Boolean,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
)
