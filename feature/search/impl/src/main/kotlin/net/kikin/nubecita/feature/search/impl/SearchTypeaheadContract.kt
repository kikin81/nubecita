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
 * [currentQuery] mirrors the latest non-debounced query set via
 * [SearchTypeaheadViewModel.setQuery] (mirrors [SearchActorsState]'s
 * field for the same reason — a stable, recompose-friendly source
 * for match-highlighting).
 */
@Immutable
internal data class SearchTypeaheadState(
    val currentQuery: String = "",
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
 *   [Suggestions.topMatch] (rendered with a "Top match" badge); the
 *   remaining actors render in the [Suggestions.people] list. When
 *   the RPC returns exactly one actor, [Suggestions.people] is empty.
 * - [NoResults]: fetch succeeded with zero actors. Renders an
 *   informational body below the CTA so the user knows the CTA
 *   commit is the next move.
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
