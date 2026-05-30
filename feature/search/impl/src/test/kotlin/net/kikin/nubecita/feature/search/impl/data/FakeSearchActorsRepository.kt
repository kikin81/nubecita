package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.data.models.ActorUi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written fake for [ActorRepository]. Same shape as
 * `FakeSearchPostsRepository` minus the `sort` axis.
 *
 *  - [respond] / [fail] register a result for a `(query, cursor)` pair
 *    by completing a [CompletableDeferred] keyed on that pair. The
 *    first registration wins: a second `respond` / `fail` for the same
 *    key calls `complete(...)` on an already-completed deferred, which
 *    the coroutines API silently no-ops. Use [clearGate] first if you
 *    need to swap the response for the same key in a single test.
 *  - [gate] returns the [CompletableDeferred] for the pair (creating
 *    it on first call); subsequent calls return the same instance.
 *    `searchActors(...)` for that key suspends on `await()` until the
 *    deferred is completed. Used by single-flight + mapLatest-cancellation
 *    tests.
 *  - [clearGate] removes a registered gate so the next `respond` / `fail`
 *    for the same key creates a fresh deferred. Used by the Retry test
 *    path where the same `(query, cursor)` is hit twice with different
 *    responses.
 *  - [setFallback] swaps the result returned when `searchActors` is
 *    called with a key that has no registered gate. Defaults to an
 *    empty page.
 *
 * [callLog] is the chronological list of every `(query, cursor, limit)`
 * the VM passed.
 */
internal class FakeSearchActorsRepository : ActorRepository {
    private data class Key(
        val query: String,
        val cursor: String?,
    )

    data class Call(
        val query: String,
        val cursor: String?,
        val limit: Int,
    )

    private val gates = mutableMapOf<Key, CompletableDeferred<Result<ActorSearchPage>>>()
    private var fallback: Result<ActorSearchPage> =
        Result.success(ActorSearchPage(items = persistentListOf(), nextCursor = null))
    val callLog: MutableList<Call> = CopyOnWriteArrayList()

    fun respond(
        query: String,
        cursor: String?,
        items: List<ActorUi>,
        nextCursor: String? = null,
    ) {
        gate(query, cursor).complete(
            Result.success(
                ActorSearchPage(
                    items = items.toImmutableList(),
                    nextCursor = nextCursor,
                ),
            ),
        )
    }

    fun fail(
        query: String,
        cursor: String?,
        throwable: Throwable,
    ) {
        gate(query, cursor).complete(Result.failure(throwable))
    }

    fun gate(
        query: String,
        cursor: String?,
    ): CompletableDeferred<Result<ActorSearchPage>> = gates.getOrPut(Key(query, cursor)) { CompletableDeferred() }

    /** Drop a registered gate so a subsequent `respond` can replace it. */
    fun clearGate(
        query: String,
        cursor: String?,
    ) {
        gates.remove(Key(query, cursor))
    }

    fun setFallback(result: Result<ActorSearchPage>) {
        fallback = result
    }

    override suspend fun searchTypeahead(
        query: String,
        limit: Int,
    ): Result<List<ActorUi>> = error("searchTypeahead is not used in search tests")

    override suspend fun searchActors(
        query: String,
        cursor: String?,
        limit: Int,
    ): Result<ActorSearchPage> {
        callLog += Call(query = query, cursor = cursor, limit = limit)
        val key = Key(query, cursor)
        val deferred = gates[key]
        return deferred?.await() ?: fallback
    }

    override fun getActor(did: String): Flow<ActorUi?> = flowOf(null)

    override fun recentActors(
        selfDid: String?,
        limit: Int,
    ): Flow<List<ActorUi>> = flowOf(emptyList())
}

/** Tiny actor fixture for VM tests. Minimal fields exercised by the VM. */
internal fun actorFixture(
    did: String = "did:plc:fake",
    handle: String = "fake.bsky.social",
    displayName: String? = "Fake",
    avatarUrl: String? = null,
): ActorUi = ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl)
