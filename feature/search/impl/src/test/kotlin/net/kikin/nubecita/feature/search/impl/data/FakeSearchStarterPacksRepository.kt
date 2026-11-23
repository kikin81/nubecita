package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.data.models.StarterPackUi
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written fake for [SearchStarterPacksRepository]. Same shape as [FakeSearchFeedsRepository]:
 * [respond] / [fail] complete the [CompletableDeferred] for a `(query, cursor)` pair, [gate]
 * hands it out so a test can hold the call open, and [setFallback] answers any other pair.
 */
internal class FakeSearchStarterPacksRepository : SearchStarterPacksRepository {
    private data class Key(
        val query: String,
        val cursor: String?,
    )

    data class Call(
        val query: String,
        val cursor: String?,
        val limit: Int,
    )

    private val gates = mutableMapOf<Key, CompletableDeferred<Result<SearchStarterPacksPage>>>()
    private var fallback: Result<SearchStarterPacksPage> =
        Result.success(SearchStarterPacksPage(items = persistentListOf(), nextCursor = null))
    val callLog: MutableList<Call> = CopyOnWriteArrayList()

    fun respond(
        query: String,
        cursor: String?,
        items: List<StarterPackUi>,
        nextCursor: String? = null,
    ) {
        gate(query, cursor).complete(Result.success(SearchStarterPacksPage(items.toImmutableList(), nextCursor)))
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
    ): CompletableDeferred<Result<SearchStarterPacksPage>> = gates.getOrPut(Key(query, cursor)) { CompletableDeferred() }

    fun clearGate(
        query: String,
        cursor: String?,
    ) {
        gates.remove(Key(query, cursor))
    }

    fun setFallback(result: Result<SearchStarterPacksPage>) {
        fallback = result
    }

    override suspend fun searchStarterPacks(
        query: String,
        cursor: String?,
        limit: Int,
    ): Result<SearchStarterPacksPage> {
        callLog += Call(query = query, cursor = cursor, limit = limit)
        return gates[Key(query, cursor)]?.await() ?: fallback
    }
}

internal fun packFixture(
    slug: String = "fake",
    name: String = "Fake Pack",
): StarterPackUi = StarterPackUiFixtures.pack(slug = slug, name = name)
