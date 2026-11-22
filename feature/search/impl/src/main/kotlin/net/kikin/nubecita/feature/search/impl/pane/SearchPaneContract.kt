package net.kikin.nubecita.feature.search.impl.pane

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch

/** A lazy list's scroll position, as the two numbers `LazyListState` is created from. */
@Immutable
internal data class ScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
)

/** What the second pane is showing. */
@Immutable
internal sealed interface SearchPaneMode {
    /** Nothing is searched: the suggestion cards. */
    data object Explore : SearchPaneMode

    /** A search is submitted. Until the summary lands this renders the same content as [Explore]. */
    data class Summary(
        val search: SubmittedSearch,
    ) : SearchPaneMode
}

/**
 * What the Summary's scroll position belongs to: the searched query and filters. It does not
 * change with the selected result tab, the filter-sheet flag or a re-submit of the same text.
 */
@Immutable
internal data class SummaryKey(
    val query: String,
    val filters: PostSearchFilters,
)

/** Which mode's scroll position a [SearchPaneEvent.ScrollSaved] belongs to. */
internal enum class ScrollTarget { Explore, Summary }

/**
 * State of the Search tab's second pane.
 *
 * The pane is composed outside the Search entry, where `rememberSaveable` is lost whenever
 * the pane leaves composition, so everything that must outlive that lives here: the scroll
 * position of each mode and the "Show more" toggles.
 */
@Immutable
internal data class SearchPaneState(
    val mode: SearchPaneMode = SearchPaneMode.Explore,
    val exploreScroll: ScrollPosition = ScrollPosition(),
    val summaryScroll: ScrollPosition = ScrollPosition(),
    /** The key [summaryScroll] belongs to; null in Explore. The Summary's list state is keyed on it. */
    val summaryKey: SummaryKey? = null,
    /** Whether the suggested-feeds grid shows every loaded feed instead of the first few. */
    val feedsExpanded: Boolean = false,
    /** Whether the people grid shows every loaded account instead of the first few. */
    val accountsExpanded: Boolean = false,
) : UiState

internal sealed interface SearchPaneEvent : UiEvent {
    /**
     * The list for a mode left composition at [position].
     *
     * [target] names the mode the list belonged to; `null` means the mode currently shown. A
     * list that leaves composition because the mode just changed must name its own mode, or
     * its position would be stored under the new one.
     */
    data class ScrollSaved(
        val position: ScrollPosition,
        val target: ScrollTarget? = null,
        /**
         * For a Summary list: the key it was created for. A save whose key is no longer the
         * current one is dropped, so a list replaced because the search changed cannot undo
         * the reset.
         */
        val summaryKey: SummaryKey? = null,
    ) : SearchPaneEvent

    data object FeedsExpansionToggled : SearchPaneEvent

    data object AccountsExpansionToggled : SearchPaneEvent
}

/** The pane has no one-shot effects: navigation and errors belong to its `DiscoverViewModel`. */
internal sealed interface SearchPaneEffect : UiEffect
