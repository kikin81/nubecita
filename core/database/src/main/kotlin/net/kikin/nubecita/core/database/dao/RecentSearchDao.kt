package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.RecentSearchEntity

/**
 * DAO for the `recent_search` table.
 *
 * [observeAll] returns rows sorted by [RecentSearchEntity.recordedAt]
 * descending (most-recent first). [upsertAndTrim] is the canonical
 * insert path: it upserts the row and trims the table to the LRU
 * capacity in a single transaction so observers see a consistent
 * post-write state.
 */
@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_search ORDER BY recorded_at DESC")
    fun observeAll(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentSearchEntity)

    @Query(
        """
        DELETE FROM recent_search
        WHERE `query` NOT IN (
            SELECT `query` FROM recent_search
            ORDER BY recorded_at DESC
            LIMIT :capacity
        )
        """,
    )
    suspend fun trimToCapacity(capacity: Int)

    @Transaction
    suspend fun upsertAndTrim(
        entity: RecentSearchEntity,
        capacity: Int,
    ) {
        upsert(entity)
        trimToCapacity(capacity)
    }

    @Query("DELETE FROM recent_search")
    suspend fun clearAll()
}
