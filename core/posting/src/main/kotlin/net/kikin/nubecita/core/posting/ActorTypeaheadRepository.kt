package net.kikin.nubecita.core.posting

/**
 * Reads suggestion candidates for the composer's `@`-mention typeahead.
 *
 * Exists because the composer needs an in-IME affordance for picking
 * canonical handles — without it, mentions silently fall through the
 * submit-time facet extractor when the user typo's the handle string
 * (`resolveHandle` fails → `app.bsky.richtext.facet#mention` is
 * dropped per the AT Protocol docs → the `@`-token renders as plain
 * text in the published record).
 *
 * Backed in production by `app.bsky.actor.searchActorsTypeahead`.
 *
 * Failure model: returns [Result.failure] for every non-success
 * outcome (network, auth, server, parse). Consumers SHALL NOT
 * distinguish failure subtypes from this surface in V1 — the composer
 * collapses any failure to a hidden dropdown (typeahead is a quality-
 * of-life surface; surfacing a snackbar on every flap of a flaky
 * connection would be more annoying than helpful, and a missed
 * typeahead never breaks the underlying submit). If a future caller
 * needs the distinction, promote to a typed sealed error then.
 *
 * Empty actor lists are [Result.success] with an empty list — a
 * "the call worked, no actors matched" outcome that the composer
 * renders as a `NoResults` row, distinct from the hidden-dropdown
 * `Result.failure` path.
 */
interface ActorTypeaheadRepository {
    /**
     * Look up actor suggestions for [query]. Caller MUST NOT prefix
     * `@` — pass the raw handle prefix the user has typed after the
     * `@` (e.g. `"al"`, `"alice.bsky"`, `"alice.bsky.so"`).
     *
     * Cancellation propagates unchanged: callers run inside the
     * composer ViewModel's `viewModelScope` and the typeahead
     * pipeline's `mapLatest` cancels in-flight calls when a newer
     * token arrives. A swallowed cancel would leak coroutines past
     * their parent.
     */
    suspend fun searchTypeahead(query: String): Result<List<ActorTypeaheadUi>>
}
