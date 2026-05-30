package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import net.kikin.nubecita.data.models.ActorUi

/**
 * DID-keyed cache of an actor's display data. Populated by `:core:actors`
 * write-through on every successful search; always overwritten so a
 * blocked/unfollowed actor's row reflects the latest live response.
 *
 * [lastSeenAt] orders "recent" surfaces (chats recipient picker, PR2) and
 * leaves room for a future eviction cap; it is NOT used for invalidation.
 */
@Entity(tableName = "actors")
data class ActorEntity(
    @PrimaryKey val did: String,
    val handle: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Instant,
    @ColumnInfo(name = "can_message", defaultValue = "1") val canMessage: Boolean = true,
)

fun ActorEntity.asExternalModel(): ActorUi = ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl, canMessage = canMessage)

fun ActorUi.toCacheEntity(lastSeenAt: Instant): ActorEntity = ActorEntity(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl, lastSeenAt = lastSeenAt, canMessage = canMessage)
