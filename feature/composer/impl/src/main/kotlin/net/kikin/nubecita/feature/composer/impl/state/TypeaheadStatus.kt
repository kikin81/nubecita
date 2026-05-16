package net.kikin.nubecita.feature.composer.impl.state

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.ActorUi

/**
 * Mutually-exclusive lifecycle for the composer's `@`-mention
 * typeahead surface.
 *
 * Modeled as a sealed interface — per the repo's MVI rule for
 * mutually-exclusive view modes — so the type system forbids
 * combinations like "Querying AND Suggestions". Distinct from
 * [ComposerSubmitStatus] and [ParentLoadStatus]: typeahead state runs
 * in parallel with both (the user can be typing into the field while
 * the parent post is still loading; the field is disabled while
 * submitting, but the typeahead state at submit-start persists until
 * the next user-driven snapshot change).
 *
 * Per the spec for `feature-composer` ("Typeahead state is a sealed
 * status sum on `ComposerState`"), exactly four variants exist:
 *
 * - [Idle]: no `@`-token under the cursor, or the active token is too
 *   short to query, or a prior query failed (failures collapse here
 *   per the design's "hide on error" decision — surfacing a snackbar
 *   on every flap of a flaky connection during typing would be more
 *   annoying than a missed typeahead).
 * - [Querying]: an XRPC call is in flight for [Querying.query]. The
 *   suggestion list is hidden in this state — flickering on every
 *   keystroke would read worse than a brief stale list.
 * - [Suggestions]: the call returned 1+ actors; the dropdown is
 *   visible.
 * - [NoResults]: the call returned an empty actor list; the dropdown
 *   is visible with a "no matches for @{query}" row, distinct from
 *   the hidden-dropdown failure path.
 *
 * Internal transient errors collapse to [Idle] before being assigned
 * to `state.typeahead`; no `Error` variant is exposed at the UI
 * boundary. If a future surface needs the distinction (e.g. a
 * "typeahead unavailable" toast), promote then.
 */
sealed interface TypeaheadStatus {
    /** No active token, or the prior query failed (hidden). */
    data object Idle : TypeaheadStatus

    /** XRPC call in flight for [query]; dropdown hidden during this state. */
    data class Querying(
        val query: String,
    ) : TypeaheadStatus

    /** Server returned 1+ actors; dropdown visible. */
    data class Suggestions(
        val query: String,
        val results: ImmutableList<ActorUi>,
    ) : TypeaheadStatus

    /** Server returned an empty actor list; dropdown visible with empty-state row. */
    data class NoResults(
        val query: String,
    ) : TypeaheadStatus
}
