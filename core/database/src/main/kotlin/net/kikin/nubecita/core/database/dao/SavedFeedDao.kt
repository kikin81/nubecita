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
 * [getAllOnce] is a one-shot snapshot of the same query — used during refresh
 * to read existing cached metadata so partially-resolved feeds can retain their
 * display name / avatar rather than being dropped.
 *
 * [upsert] is a full-row overwrite: on conflict with an existing [SavedFeedEntity.uri]
 * every column is replaced with the supplied values. Callers must always provide
 * a complete [SavedFeedEntity] — partial updates are not supported (use
 * [setPinned] for the pin toggle).
 *
 * [deleteUrisNotIn] is used after a refresh to prune feeds that have been
 * removed from the server-side list — pass the URIs you want to keep.
 *
 * WARNING: never call [deleteUrisNotIn] with an empty list — Room cannot bind
 * an empty list to an `IN` clause and throws at runtime. When the refresh
 * resolves to zero feeds (e.g. the user has none), call [clear] instead.
 */
@Dao
interface SavedFeedDao {
    @Query("SELECT * FROM saved_feeds ORDER BY position ASC")
    fun observeSavedFeeds(): Flow<List<SavedFeedEntity>>

    /** One-shot snapshot of all cached feeds, used during refresh for metadata fallback. */
    @Query("SELECT * FROM saved_feeds ORDER BY position ASC")
    suspend fun getAllOnce(): List<SavedFeedEntity>

    @Upsert
    suspend fun upsert(feeds: List<SavedFeedEntity>)

    @Query("UPDATE saved_feeds SET pinned = :pinned WHERE uri = :uri")
    suspend fun setPinned(
        uri: String,
        pinned: Boolean,
    )

    /** Prune feeds absent from [keepUris]. MUST be non-empty — use [clear] for the empty case. */
    @Query("DELETE FROM saved_feeds WHERE uri NOT IN (:keepUris)")
    suspend fun deleteUrisNotIn(keepUris: List<String>)

    /** Removes every cached saved feed — the safe path when a refresh resolves to zero feeds. */
    @Query("DELETE FROM saved_feeds")
    suspend fun clear()
}
