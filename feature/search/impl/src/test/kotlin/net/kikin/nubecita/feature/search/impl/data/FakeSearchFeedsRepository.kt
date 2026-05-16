package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written fake for [SearchFeedsRepository]. Same shape as
 * [FakeSearchActorsRepository].
 *
 *  - [respond] / [fail] register a result for a `(query, cursor)` pair
 *    by completing a [CompletableDeferred] keyed on that pair.
 *  - [gate] returns the [CompletableDeferred] for the pair (creating
 *    it on first call); subsequent calls return the same instance.
 *    `searchFeeds(...)` for that key suspends on `await()` until the
 *    deferred is completed.
 *  - [clearGate] removes a registered gate so the next `respond` / `fail`
 *    for the same key creates a fresh deferred.
 *  - [setFallback] swaps the result returned when `searchFeeds` is
 *    called with a key that has no registered gate.
 */
internal class FakeSearchFeedsRepository : SearchFeedsRepository {
    private data class Key(
        val query: String,
        val cursor: String?,
    )

    data class Call(
        val query: String,
        val cursor: String?,
        val limit: Int,
    )

    private val gates = mutableMapOf<Key, CompletableDeferred<Result<SearchFeedsPage>>>()
    private var fallback: Result<SearchFeedsPage> =
        Result.success(SearchFeedsPage(items = persistentListOf(), nextCursor = null))
    val callLog: MutableList<Call> = CopyOnWriteArrayList()

    fun respond(
        query: String,
        cursor: String?,
        items: List<FeedGeneratorUi>,
        nextCursor: String? = null,
    ) {
        gate(query, cursor).complete(
            Result.success(
                SearchFeedsPage(
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
    ): CompletableDeferred<Result<SearchFeedsPage>> = gates.getOrPut(Key(query, cursor)) { CompletableDeferred() }

    fun clearGate(
        query: String,
        cursor: String?,
    ) {
        gates.remove(Key(query, cursor))
    }

    fun setFallback(result: Result<SearchFeedsPage>) {
        fallback = result
    }

    override suspend fun searchFeeds(
        query: String,
        cursor: String?,
        limit: Int,
    ): Result<SearchFeedsPage> {
        callLog += Call(query = query, cursor = cursor, limit = limit)
        val key = Key(query, cursor)
        val deferred = gates[key]
        return deferred?.await() ?: fallback
    }
}

internal fun feedFixture(
    uri: String = "at://did:plc:fake/app.bsky.feed.generator/fake",
    displayName: String = "Fake Feed",
    creatorHandle: String = "fake.bsky.social",
    creatorDisplayName: String? = "Fake",
    description: String? = "Fake feed for testing.",
    avatarUrl: String? = null,
    likeCount: Long = 0L,
): FeedGeneratorUi =
    FeedGeneratorUi(
        uri = uri,
        displayName = displayName,
        creatorHandle = creatorHandle,
        creatorDisplayName = creatorDisplayName,
        description = description,
        avatarUrl = avatarUrl,
        likeCount = likeCount,
    )
