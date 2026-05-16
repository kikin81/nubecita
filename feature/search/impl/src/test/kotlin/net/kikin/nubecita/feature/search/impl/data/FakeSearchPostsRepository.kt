package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Instant

/**
 * Hand-written fake for [SearchPostsRepository]. Two configuration
 * paths:
 *
 *  - [respond] / [fail] register a result for a `(query, cursor, sort)`
 *    triple that the VM will hit. The result is completed via the gate
 *    deferred so a follow-up call to [gate] for the same triple returns
 *    a still-pending deferred (used by single-flight + mapLatest tests).
 *  - [gate] returns a [CompletableDeferred] for the triple, suspending
 *    the call until the test completes it. Used by single-flight +
 *    mapLatest-cancellation tests.
 *
 * [callLog] is the chronological list of every `(query, cursor, sort)`
 * the VM passed; tests use it to assert that `mapLatest` cancelled
 * stale calls and that pagination passes through the right cursor.
 */
internal class FakeSearchPostsRepository : SearchPostsRepository {
    private data class Key(
        val query: String,
        val cursor: String?,
        val sort: SearchPostsSort,
    )

    data class Call(
        val query: String,
        val cursor: String?,
        val sort: SearchPostsSort,
        val limit: Int,
    )

    private val gates = mutableMapOf<Key, CompletableDeferred<Result<SearchPostsPage>>>()
    private var fallback: Result<SearchPostsPage> =
        Result.success(SearchPostsPage(items = persistentListOf(), nextCursor = null))
    val callLog: MutableList<Call> = CopyOnWriteArrayList()

    /** Register an immediate response for the given key. */
    fun respond(
        query: String,
        cursor: String?,
        sort: SearchPostsSort,
        items: List<FeedItemUi.Single>,
        nextCursor: String? = null,
    ) {
        gate(query, cursor, sort).complete(
            Result.success(
                SearchPostsPage(
                    items = items.toImmutableList(),
                    nextCursor = nextCursor,
                ),
            ),
        )
    }

    /** Register a failure for the given key. */
    fun fail(
        query: String,
        cursor: String?,
        sort: SearchPostsSort,
        throwable: Throwable,
    ) {
        gate(query, cursor, sort).complete(Result.failure(throwable))
    }

    /**
     * Returns a deferred for the given key; subsequent gate calls for
     * the same key return the same instance. Suspends [searchPosts]
     * for that key until the test completes the deferred.
     */
    fun gate(
        query: String,
        cursor: String?,
        sort: SearchPostsSort,
    ): CompletableDeferred<Result<SearchPostsPage>> = gates.getOrPut(Key(query, cursor, sort)) { CompletableDeferred() }

    /**
     * Configure the response returned when no gate is registered for
     * the requested key. Default is an empty page with no cursor.
     */
    fun setFallback(result: Result<SearchPostsPage>) {
        fallback = result
    }

    /**
     * Clear a previously-registered (and possibly already-completed) gate
     * for the given key. Tests that drive the VM through repeated calls
     * to the same key (e.g. Retry after InitialError) use this to swap a
     * fresh response in for the next call.
     */
    fun clearGate(
        query: String,
        cursor: String?,
        sort: SearchPostsSort,
    ) {
        gates.remove(Key(query, cursor, sort))
    }

    override suspend fun searchPosts(
        query: String,
        cursor: String?,
        limit: Int,
        sort: SearchPostsSort,
    ): Result<SearchPostsPage> {
        callLog += Call(query = query, cursor = cursor, sort = sort, limit = limit)
        val key = Key(query, cursor, sort)
        val deferred = gates[key]
        return deferred?.await() ?: fallback
    }
}

internal fun searchPostFixture(
    uri: String,
    text: String,
): FeedItemUi.Single =
    FeedItemUi.Single(
        post =
            PostUi(
                id = uri,
                cid = "bafyreifakecid000000000000000000000000000000000",
                author =
                    AuthorUi(
                        did = "did:plc:fake",
                        handle = "fake.bsky.social",
                        displayName = "Fake",
                        avatarUrl = null,
                    ),
                createdAt = Instant.parse("2026-04-25T12:00:00Z"),
                text = text,
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            ),
    )
