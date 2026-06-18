package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Search tab home (search bar + chip strip / results).
 *
 * [currentQuery] is the debounced, trimmed view of [SearchViewModel.textFieldState].
 * It drives the expanded overlay's typeahead surface (recents when blank,
 * [SearchTypeaheadScreen] when non-blank); the per-tab Screens (vrba.6 / vrba.7)
 * subscribe via the screen Composable.
 *
 * [recentSearches] mirrors the LRU list owned by `RecentSearchRepository`.
 * It is empty when the user has no recent searches; the chip-strip composable
 * does not render in that case.
 *
 * [phase] is the mutually-exclusive lifecycle sum that drives the *body*
 * (the surface beneath the collapsed search bar). See [SearchPhase] KDoc.
 */
@Immutable
data class SearchScreenViewState(
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val currentQuery: String = "",
    val isQueryBlank: Boolean = true,
    val phase: SearchPhase = SearchPhase.Discover,
) : UiState

/**
 * Mutually-exclusive lifecycle for the Search tab home *body* — the surface
 * beneath the collapsed search bar. Drives [SearchScreenContent]'s
 * `when (phase)` branching:
 *
 * - [Discover]: no query has been submitted → render the recent-search chip
 *   strip (or an empty body if no recents).
 * - [Results]: a query has been submitted (IME action, "Search for {q}" CTA,
 *   recent chip tap) → render the SecondaryTabRow + HorizontalPager hosting
 *   the per-tab Screens (Posts / People / Feeds).
 *
 * Typeahead is intentionally NOT a body phase: while the user is typing, the
 * search bar is expanded and the typeahead suggestions render inside the
 * expanded overlay (driven by [SearchScreenViewState.currentQuery]). The body
 * underneath keeps showing the last submitted query's results, or Discover.
 */
@Immutable
sealed interface SearchPhase {
    @Immutable
    data object Discover : SearchPhase

    @Immutable
    data class Results(
        val query: String,
        /**
         * True when [query] arrived via a recent-search chip tap, false when
         * typed/submitted fresh. Threaded to each tab VM so the
         * `search_perform` analytics event can carry `from_recent` — only the
         * boolean is logged, the query text itself is never sent.
         */
        val fromRecent: Boolean = false,
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
