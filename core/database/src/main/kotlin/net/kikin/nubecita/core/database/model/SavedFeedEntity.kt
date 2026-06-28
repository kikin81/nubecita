package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Row in the `saved_feeds` table. Mirrors the user's saved/pinned feed list
 * as returned by `app.bsky.feed.getSavedFeeds`. Populated by `:core:feeds`
 * write-through on every refresh; `position` preserves server-side order so
 * the local cache renders feeds in the same sequence without a network call.
 *
 * [creatorHandle] and [avatarUrl] are nullable — generator feeds (e.g. the
 * Discover feed) may omit creator info. [pinned] reflects the server's
 * `pinned` flag and drives pin-indicator UI without a round-trip.
 *
 * No [asExternalModel] extension here — mapping to the `:data:models` UI type
 * is the responsibility of `:core:feeds` (Task 2), keeping this module free of
 * any feature-layer dependency.
 */
@Entity(tableName = "saved_feeds")
data class SavedFeedEntity(
    @PrimaryKey val uri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "creator_handle") val creatorHandle: String?,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    val pinned: Boolean,
    val position: Int,
)
