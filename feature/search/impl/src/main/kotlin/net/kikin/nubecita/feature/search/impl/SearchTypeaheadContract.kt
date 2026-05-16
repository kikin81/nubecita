package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

/**
 * MVI state for the mid-query Search typeahead screen (vrba.10).
 *
 * The screen renders BETWEEN the recent-search chip strip and the
 * full results page — whenever the parent [SearchViewModel]'s phase
 * is [SearchPhase.Typeahead]. The primary "Search for {q}" CTA at
 * the top is rendered unconditionally; everything below is driven
 * by [status].
 *
 * Match-highlighting reads the query off each [SearchTypeaheadStatus]
 * variant's `query` payload (the variant captures the exact query the
 * fetch was issued for), so this state intentionally has no top-level
 * `currentQuery` field — the parent [SearchViewModel] is the canonical
 * source for "what the user is typing right now," forwarded via the
 * `currentQuery` parameter on the screen Composable.
 */
@Immutable
internal data class SearchTypeaheadState(
    val status: SearchTypeaheadStatus = SearchTypeaheadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle for the typeahead screen.
 *
 * - [Idle]: query is blank, OR the previous fetch failed silently.
 *   The screen body below the CTA renders empty. Per the
 *   [net.kikin.nubecita.core.posting.ActorTypeaheadRepository]
 *   contract, transient failures collapse here — typeahead is a
 *   quality-of-life surface, not worth a snackbar on every flap of
 *   a flaky connection. Mirrors `:feature:composer:impl`'s
 *   `TypeaheadStatus.Idle`.
 * - [Loading]: fetch in flight for [Loading.query]. Renders an
 *   indeterminate progress indicator below the CTA.
 * - [Suggestions]: results in hand. The first actor is hoisted into
 *   [Suggestions.topMatch] (rendered under a "Top match" section
 *   header); the remaining actors render under a "People" section
 *   header in the [Suggestions.people] list. When the RPC returns
 *   exactly one actor, [Suggestions.people] is empty and the People
 *   header is omitted.
 * - [NoResults]: fetch succeeded with zero actors. The screen body
 *   below the CTA renders empty — the CTA already communicates the
 *   next move ("Search for {q}"), so adding a per-status empty-state
 *   line would be redundant. If product wants an explicit "no
 *   matches" affordance later, render it here.
 */
@Immutable
internal sealed interface SearchTypeaheadStatus {
    @Immutable
    data object Idle : SearchTypeaheadStatus

    @Immutable
    data class Loading(
        val query: String,
    ) : SearchTypeaheadStatus

    @Immutable
    data class Suggestions(
        val query: String,
        val topMatch: ActorUi,
        val people: ImmutableList<ActorUi>,
    ) : SearchTypeaheadStatus

    @Immutable
    data class NoResults(
        val query: String,
    ) : SearchTypeaheadStatus
}

internal sealed interface SearchTypeaheadEvent : UiEvent {
    /** Tap on the top-match row or any actor in the People list. */
    data class ActorTapped(
        val handle: String,
    ) : SearchTypeaheadEvent
}

internal sealed interface SearchTypeaheadEffect : UiEffect {
    /** Push [net.kikin.nubecita.feature.profile.api.Profile] onto the MainShell nav stack. */
    data class NavigateToProfile(
        val handle: String,
    ) : SearchTypeaheadEffect
}
