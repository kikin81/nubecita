package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.core.database.model.SavedFeedEntity

/**
 * DAO for the `saved_feeds` table.
 *
 * [observeSavedFeeds] emits the full ordered list on every write, allowing
 * callers to react to remote refreshes without polling. The list is always
 * ordered by [SavedFeedEntity.position] ascending — matching the server's
 * canonical ordering.
 *
 * [upsert] is a full-row overwrite: on conflict with an existing [SavedFeedEntity.uri]
 * every column is replaced with the supplied values. Callers must always provide
 * a complete [SavedFeedEntity] — partial updates are not supported (use
 * [setPinned] for the pin toggle).
 *
 * [deleteUrisNotIn] is used after a refresh to prune feeds that have been
 * removed from the server-side list — pass the URIs you want to keep.
 */
@Dao
interface SavedFeedDao {
    @Query("SELECT * FROM saved_feeds ORDER BY position ASC")
    fun observeSavedFeeds(): Flow<List<SavedFeedEntity>>

    @Upsert
    suspend fun upsert(feeds: List<SavedFeedEntity>)

    @Query("UPDATE saved_feeds SET pinned = :pinned WHERE uri = :uri")
    suspend fun setPinned(
        uri: String,
        pinned: Boolean,
    )

    @Query("DELETE FROM saved_feeds WHERE uri NOT IN (:keepUris)")
    suspend fun deleteUrisNotIn(keepUris: List<String>)
}
