package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.kikin.nubecita.data.models.ActorUi
import kotlin.time.Instant

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
    // `defaultValue = "0"` is the SQL default the v3→v4 AutoMigration uses to
    // backfill rows cached *before* this column existed: their DM-eligibility was
    // never computed, so we treat them as NOT messageable (fail-closed) and let a
    // fresh search overwrite the real value. Every app write binds `canMessage`
    // explicitly (write-through), so the SQL default only governs that backfill.
    // The Kotlin default (`= true`) is the construction fail-open used for previews
    // /fixtures and is intentionally distinct from the migration's fail-closed.
    @ColumnInfo(name = "can_message", defaultValue = "0") val canMessage: Boolean = true,
)

fun ActorEntity.asExternalModel(): ActorUi = ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl, canMessage = canMessage)

fun ActorUi.toCacheEntity(lastSeenAt: Instant): ActorEntity = ActorEntity(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl, lastSeenAt = lastSeenAt, canMessage = canMessage)
