package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.kikin.nubecita.core.database.model.TranslationEntity

/**
 * DAO for the `post_translation` cache.
 *
 * Reads are `suspend`, not `Flow`, which departs from the module's usual
 * convention on purpose: a cached translation is fetched once at the moment a
 * post needs it and never changes afterwards. A `Flow` per visible post would
 * mean one observer per card on a list that has to hold 120hz, in exchange for
 * emissions that never come.
 */
@Dao
interface TranslationDao {
    @Query(
        """
        SELECT * FROM post_translation
        WHERE post_uri = :postUri AND target_language = :targetLanguage
        """,
    )
    suspend fun find(
        postUri: String,
        targetLanguage: String,
    ): TranslationEntity?

    /**
     * Cache a translation and trim the table back to [capacity] rows, oldest
     * first, in one transaction.
     *
     * Bounded by count rather than by age: what makes the cache worth having
     * is the post a reader scrolls past and back to within a session, and a
     * time bound would either expire those or keep thousands of rows from a
     * long scroll. Trimming inside the transaction means a reader can never
     * observe the table over its bound.
     */
    @Transaction
    suspend fun upsertAndTrim(
        entity: TranslationEntity,
        capacity: Int = DEFAULT_CAPACITY,
    ) {
        upsert(entity)
        trimTo(capacity)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranslationEntity)

    @Query(
        """
        DELETE FROM post_translation
        WHERE rowid NOT IN (
            SELECT rowid FROM post_translation ORDER BY created_at DESC LIMIT :capacity
        )
        """,
    )
    suspend fun trimTo(capacity: Int)

    /** Drop every cached translation into [targetLanguage]. */
    @Query("DELETE FROM post_translation WHERE target_language = :targetLanguage")
    suspend fun deleteForLanguage(targetLanguage: String)

    @Query("DELETE FROM post_translation")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM post_translation")
    suspend fun count(): Int

    companion object {
        /**
         * Roughly a long session's worth of foreign-language posts. Each row
         * is a few hundred bytes, so the whole cache stays far below the cost
         * of a single language pack.
         */
        const val DEFAULT_CAPACITY: Int = 500
    }
}
