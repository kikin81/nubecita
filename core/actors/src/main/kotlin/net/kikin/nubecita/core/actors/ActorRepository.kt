package net.kikin.nubecita.core.actors

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.data.models.ActorUi

/**
 * Single seam for actor discovery + display. Network search (typeahead +
 * paginated) plus a DID-keyed local cache populated by write-through.
 *
 * Network methods return [Result]; CancellationException always propagates.
 * No error UX is baked in — each consumer maps failures itself (search's
 * People tab uses a typed error sum; the composer dropdown hides on any
 * failure). Empty matches are [Result.success] with an empty list.
 *
 * Every successful search upserts its actors into the cache (always
 * overwriting), so [getActor] reflects the freshest sighting — a blocked
 * or unfollowed actor is replaced by the live response, never resurrected
 * from a stale query-result cache.
 */
interface ActorRepository {
    /** Fast as-you-type suggestions — `app.bsky.actor.searchActorsTypeahead`. Single-shot. */
    suspend fun searchTypeahead(
        query: String,
        limit: Int = 8,
    ): Result<List<ActorUi>>

    /** Full paginated search — `app.bsky.actor.searchActors`. */
    suspend fun searchActors(
        query: String,
        cursor: String? = null,
        limit: Int = 25,
    ): Result<ActorSearchPage>

    /** Observe a single cached actor by DID; emits null when not cached. */
    fun getActor(did: String): Flow<ActorUi?>

    /** Recently-seen actors (most recent first) from the cache, excluding [selfDid]. */
    fun recentActors(
        selfDid: String?,
        limit: Int = 20,
    ): Flow<List<ActorUi>>
}

/** One page of [ActorRepository.searchActors] results. */
data class ActorSearchPage(
    val items: ImmutableList<ActorUi>,
    val nextCursor: String?,
)
