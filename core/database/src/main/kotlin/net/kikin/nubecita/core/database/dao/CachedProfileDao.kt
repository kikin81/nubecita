package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import net.kikin.nubecita.core.database.model.CachedProfileEntity
import kotlin.time.Instant

/**
 * DAO for `cached_profile`. Every query is scoped to one account: the blob
 * embeds that viewer's relationship to the profile, so one identity must never
 * read another's rows.
 *
 * Lookups take an `actor` — a DID or a handle — because profile routes carry
 * either.
 */
@Dao
interface CachedProfileDao {
    @Upsert
    suspend fun upsert(row: CachedProfileEntity)

    @Query(
        "SELECT * FROM cached_profile " +
            "WHERE account_did = :accountDid AND (did = :actor OR handle = :actor) LIMIT 1",
    )
    suspend fun byActor(
        accountDid: String,
        actor: String,
    ): CachedProfileEntity?

    @Query("UPDATE cached_profile SET last_opened_at = :at WHERE account_did = :accountDid AND did = :did")
    suspend fun touch(
        accountDid: String,
        did: String,
        at: Instant,
    )

    @Query("DELETE FROM cached_profile WHERE account_did = :accountDid AND (did = :actor OR handle = :actor)")
    suspend fun deleteByActor(
        accountDid: String,
        actor: String,
    )

    /**
     * Keeps at most [cap] rows: [keepDid] (the account's own profile, which
     * must never age out) plus the `cap - 1` most recently opened others.
     */
    @Query(
        "DELETE FROM cached_profile " +
            "WHERE account_did = :accountDid AND did != :keepDid " +
            "AND did NOT IN (" +
            "SELECT did FROM cached_profile WHERE account_did = :accountDid AND did != :keepDid " +
            "ORDER BY last_opened_at DESC LIMIT :cap - 1)",
    )
    suspend fun trimToCap(
        accountDid: String,
        cap: Int,
        keepDid: String,
    )

    @Query("DELETE FROM cached_profile WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
