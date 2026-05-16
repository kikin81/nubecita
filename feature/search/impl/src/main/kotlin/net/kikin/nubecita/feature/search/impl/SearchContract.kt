package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Search tab home (input row + chip strip / typeahead / results).
 *
 * [currentQuery] is the debounced, trimmed view of [SearchViewModel.textFieldState];
 * the typeahead screen (vrba.10) and the per-tab Screens (vrba.6 / vrba.7) subscribe
 * via the screen Composable.
 *
 * [recentSearches] mirrors the LRU list owned by `RecentSearchRepository`.
 * It is empty when the user has no recent searches; the chip-strip composable
 * does not render in that case.
 *
 * [phase] is the mutually-exclusive lifecycle sum that drives the body
 * branching in [SearchScreenContent]. See [SearchPhase] KDoc for the
 * Discover → Typeahead → Results transitions.
 */
@Immutable
data class SearchScreenViewState(
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val currentQuery: String = "",
    val isQueryBlank: Boolean = true,
    val phase: SearchPhase = SearchPhase.Discover,
) : UiState

/**
 * Mutually-exclusive lifecycle for the Search tab home body. Drives
 * [SearchScreenContent]'s `when (phase)` branching:
 *
 * - [Discover]: query is blank → render the recent-search chip strip
 *   (or an empty body if no recents).
 * - [Typeahead]: query is non-blank and has not yet been submitted →
 *   render the grouped typeahead suggestions screen (vrba.10) with
 *   the primary "Search for {q}" CTA at the top.
 * - [Results]: query has been submitted (IME action, CTA, recent
 *   chip tap) → render the SecondaryTabRow + HorizontalPager hosting
 *   the per-tab Screens (Posts / People).
 *
 * Editing the text after submission re-enters [Typeahead] until the
 * next submission, mirroring the Bluesky web search behavior.
 */
@Immutable
sealed interface SearchPhase {
    @Immutable
    data object Discover : SearchPhase

    @Immutable
    data class Typeahead(
        val query: String,
    ) : SearchPhase

    @Immutable
    data class Results(
        val query: String,
    ) : SearchPhase
}

sealed interface SearchEvent : UiEvent {
    /** IME action `Search` / hardware Enter. Persists the current non-blank text. */
    data object SubmitClicked : SearchEvent

    /** Tap on a chip body. Seeds the text field and bumps the chip's recency. */
    data class RecentChipTapped(
        val query: String,
    ) : SearchEvent

    /** Tap on the trailing X icon on a chip. Removes that chip. */
    data class RecentChipRemoved(
        val query: String,
    ) : SearchEvent

    /** Overflow-menu "Clear all" item. Wipes the recent-search list. */
    data object ClearAllRecentsClicked : SearchEvent
}

/**
 * Empty for vrba.5. The cross-VM orchestration (vrba.8) emits a
 * `NavigateTo(target: NavKey)` effect when the user taps a post or actor
 * row in the (still-pending) tab content; until then this screen signals
 * nothing externally.
 */
sealed interface SearchEffect : UiEffect
