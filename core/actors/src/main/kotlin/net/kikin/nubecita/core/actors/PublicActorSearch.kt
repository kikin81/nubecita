package net.kikin.nubecita.core.actors

import net.kikin.nubecita.data.models.ActorUi

/**
 * Account lookup for surfaces that run **before there is a session** — today,
 * the login screen's handle typeahead.
 *
 * Separate from [ActorRepository] rather than another method on it, for two
 * reasons. [ActorRepository] resolves the signed-in user's own PDS via
 * `XrpcClientProvider.authenticated()`, which throws by design when there is no
 * session, so every one of its methods is unusable here. And login needs
 * exactly one call — handing it the whole repository surface would invite a
 * pre-login caller to reach for something that cannot work.
 *
 * Results are NOT cached. They belong to someone who has not signed in yet, so
 * writing them into the actor cache would leave one account's search history in
 * the next account's local database.
 */
interface PublicActorSearch {
    /**
     * Suggestions for a partial handle or name, via the public AppView's
     * `app.bsky.actor.searchActorsTypeahead`, which serves anonymous callers.
     *
     * Single-shot. Network failures come back as [Result.failure]; cancellation
     * always propagates so a superseded keystroke unwinds cleanly.
     */
    suspend fun searchTypeahead(
        query: String,
        limit: Int = 8,
    ): Result<List<ActorUi>>

    /**
     * The PDS hosting [did], or `null` when it cannot be determined.
     *
     * Null rather than a failure because this is decoration: the suggestion is
     * still selectable and login still works without it, so a caller should
     * render the row and leave the line blank rather than treat it as an error.
     *
     * Costs one DID-document fetch (measured 300–1100ms), so callers must not
     * block a list on it.
     */
    suspend fun resolvePdsHost(did: String): String?
}
