package net.kikin.nubecita.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import net.kikin.nubecita.core.database.model.CachedPostEntity

/**
 * DAO for the `cached_post` table — the one store for post content, keyed by
 * `(accountDid, uri)`.
 *
 * Writes are `@Upsert` because the same post legitimately arrives more than
 * once: as a timeline entry on one page and as another entry's reply context on
 * another. Upsert is what makes "stored once" true rather than aspirational —
 * the later arrival refreshes the row instead of conflicting with it or
 * creating a second copy.
 *
 * Eviction is reference-aware, which is why it does not live in
 * [FeedPostDao.trimToCap]: a context post holds no position in any partition,
 * so position arithmetic cannot see it. [deleteUnreferenced] deletes rows that
 * no feed-partition row points at. Once the grouped projection table lands it
 * joins that NOT EXISTS clause, so a post kept alive only by a cluster's
 * context is not evicted from under it.
 */
@Dao
interface CachedPostDao {
    @Upsert
    suspend fun upsert(posts: List<CachedPostEntity>)

    @Query("SELECT * FROM cached_post WHERE account_did = :accountDid AND uri = :uri")
    suspend fun byUri(
        accountDid: String,
        uri: String,
    ): CachedPostEntity?

    @Query("SELECT * FROM cached_post WHERE account_did = :accountDid AND uri IN (:uris)")
    suspend fun byUris(
        accountDid: String,
        uris: List<String>,
    ): List<CachedPostEntity>

    @Query("SELECT COUNT(*) FROM cached_post WHERE account_did = :accountDid")
    suspend fun countForAccount(accountDid: String): Int

    /**
     * Deletes this account's posts that no feed-partition row references.
     *
     * Scoped to one account so a sign-out or an eviction pass on one identity
     * cannot reach another's rows.
     */
    @Query(
        "DELETE FROM cached_post " +
            "WHERE account_did = :accountDid " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM feed_post " +
            "WHERE feed_post.account_did = cached_post.account_did " +
            "AND feed_post.uri = cached_post.uri) " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM feed_item_member " +
            "WHERE feed_item_member.account_did = cached_post.account_did " +
            "AND feed_item_member.uri = cached_post.uri)",
    )
    suspend fun deleteUnreferenced(accountDid: String)

    @Query("DELETE FROM cached_post WHERE account_did = :accountDid")
    suspend fun clearAccount(accountDid: String)
}
