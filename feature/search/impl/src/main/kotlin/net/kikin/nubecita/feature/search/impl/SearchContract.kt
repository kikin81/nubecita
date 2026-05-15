package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Search tab home (input row + recent-search chips).
 *
 * [currentQuery] is the debounced, trimmed view of [SearchViewModel.textFieldState];
 * the search-tab content (vrba.6 / vrba.7 / vrba.8) will subscribe to it via
 * the screen Composable and re-issue searchPosts / searchActors RPCs whenever
 * it changes.
 *
 * [recentSearches] mirrors the LRU list owned by `RecentSearchRepository`.
 * It is empty when the user has no recent searches; the chip-strip composable
 * does not render in that case.
 */
@Immutable
data class SearchScreenViewState(
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val currentQuery: String = "",
    val isQueryBlank: Boolean = true,
) : UiState

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
