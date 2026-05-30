package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.ActorEntity

/**
 * DAO for the `actors` table.
 *
 * [getActor] emits `null` as its first emission when no row with the
 * given [did] exists, then re-emits the matching [ActorEntity] whenever
 * the underlying row is inserted or updated by a subsequent [upsert] call.
 *
 * [upsert] is a full-row overwrite: on conflict with an existing [did]
 * every column is replaced with the supplied values. Callers must always
 * provide a complete [ActorEntity] — partial updates are not supported.
 */
@Dao
interface ActorDao {
    @Upsert
    suspend fun upsert(actors: List<ActorEntity>)

    @Query("SELECT * FROM actors WHERE did = :did")
    fun getActor(did: String): Flow<ActorEntity?>

    @Query(
        "SELECT * FROM actors WHERE :selfDid IS NULL OR did <> :selfDid " +
            "ORDER BY last_seen_at DESC LIMIT :limit",
    )
    fun recentActors(
        selfDid: String?,
        limit: Int,
    ): Flow<List<ActorEntity>>
}
