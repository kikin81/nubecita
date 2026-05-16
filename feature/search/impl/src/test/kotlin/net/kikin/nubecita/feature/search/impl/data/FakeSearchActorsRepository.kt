package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.data.models.ActorUi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written fake for [SearchActorsRepository]. Same shape as
 * `FakeSearchPostsRepository` minus the `sort` axis.
 *
 *  - [respond] / [fail] register a result for a `(query, cursor)`
 *    pair. The first matching call wins; subsequent calls fall back to
 *    [fallback] (default: empty page).
 *  - [gate] returns a [CompletableDeferred] for the pair, suspending
 *    the call until the test completes it. Used by single-flight +
 *    mapLatest-cancellation tests.
 *  - [clearGate] removes a registered gate so a subsequent `respond`
 *    can replace it (used by the Retry test path).
 *
 * [callLog] is the chronological list of every `(query, cursor)` the
 * VM passed.
 */
internal class FakeSearchActorsRepository : SearchActorsRepository {
    private data class Key(
        val query: String,
        val cursor: String?,
    )

    data class Call(
        val query: String,
        val cursor: String?,
        val limit: Int,
    )

    private val gates = mutableMapOf<Key, CompletableDeferred<Result<SearchActorsPage>>>()
    private var fallback: Result<SearchActorsPage> =
        Result.success(SearchActorsPage(items = persistentListOf(), nextCursor = null))
    val callLog: MutableList<Call> = CopyOnWriteArrayList()

    fun respond(
        query: String,
        cursor: String?,
        items: List<ActorUi>,
        nextCursor: String? = null,
    ) {
        gate(query, cursor).complete(
            Result.success(
                SearchActorsPage(
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
    ): CompletableDeferred<Result<SearchActorsPage>> = gates.getOrPut(Key(query, cursor)) { CompletableDeferred() }

    /** Drop a registered gate so a subsequent `respond` can replace it. */
    fun clearGate(
        query: String,
        cursor: String?,
    ) {
        gates.remove(Key(query, cursor))
    }

    fun setFallback(result: Result<SearchActorsPage>) {
        fallback = result
    }

    override suspend fun searchActors(
        query: String,
        cursor: String?,
        limit: Int,
    ): Result<SearchActorsPage> {
        callLog += Call(query = query, cursor = cursor, limit = limit)
        val key = Key(query, cursor)
        val deferred = gates[key]
        return deferred?.await() ?: fallback
    }
}

/** Tiny actor fixture for VM tests. Minimal fields exercised by the VM. */
internal fun actorFixture(
    did: String = "did:plc:fake",
    handle: String = "fake.bsky.social",
    displayName: String? = "Fake",
    avatarUrl: String? = null,
): ActorUi = ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl)
