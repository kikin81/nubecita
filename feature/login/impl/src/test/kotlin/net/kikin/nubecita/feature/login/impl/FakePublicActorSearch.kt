package net.kikin.nubecita.feature.login.impl

import net.kikin.nubecita.core.actors.PublicActorSearch
import net.kikin.nubecita.data.models.ActorUi

/**
 * Scripted typeahead. Suggestions and hosts are set per test rather than
 * derived from the query, so a test states exactly what the network returns.
 *
 * [queries] records every call in order — the debounce and cancellation tests
 * assert on the *number* of queries that got through, which is the behaviour
 * those tests exist to pin.
 */
internal class FakePublicActorSearch : PublicActorSearch {
    val queries = mutableListOf<String>()
    var result: Result<List<ActorUi>> = Result.success(emptyList())
    var hosts: Map<String, String> = emptyMap()

    /** Set to suspend [searchTypeahead] so a test can supersede an in-flight query. */
    var gate: (suspend () -> Unit)? = null

    override suspend fun searchTypeahead(
        query: String,
        limit: Int,
    ): Result<List<ActorUi>> {
        queries += query
        gate?.invoke()
        return result
    }

    override suspend fun resolvePdsHost(did: String): String? = hosts[did]
}
