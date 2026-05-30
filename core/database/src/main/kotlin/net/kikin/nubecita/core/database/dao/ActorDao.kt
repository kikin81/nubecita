package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.ActorEntity

@Dao
interface ActorDao {
    @Upsert
    suspend fun upsert(actors: List<ActorEntity>)

    @Query("SELECT * FROM actors WHERE did = :did")
    fun getActor(did: String): Flow<ActorEntity?>
}
