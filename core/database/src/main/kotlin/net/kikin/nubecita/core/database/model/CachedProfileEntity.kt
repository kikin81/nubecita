package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import kotlin.time.Instant

/**
 * One cached profile per account, keyed by the profile's DID.
 *
 * [profileBlob] holds the wire `app.bsky.actor.defs#profileViewDetailed` —
 * the same shape `getProfile` returns — so new lexicon fields (pronouns was
 * one) reach the UI without another schema bump, and the existing wire → UI
 * mapper stays the single mapping path. [handle] is denormalized so a route
 * that carries a handle instead of a DID still finds its row.
 *
 * Per-account because the blob embeds the viewer's relationship to the
 * profile (follow, mute, block). Rows are a speed layer, never the source of
 * truth: every cached render is followed by a `getProfile` refresh that
 * overwrites the row. [lastOpenedAt] orders the per-account cap.
 */
@Entity(
    tableName = "cached_profile",
    primaryKeys = ["account_did", "did"],
    indices = [Index(value = ["account_did", "handle"])],
)
data class CachedProfileEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    val did: String,
    val handle: String,
    @ColumnInfo(name = "profile_blob") val profileBlob: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Instant,
)
