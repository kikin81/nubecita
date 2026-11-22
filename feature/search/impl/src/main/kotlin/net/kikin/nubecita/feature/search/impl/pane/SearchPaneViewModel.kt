package net.kikin.nubecita.feature.search.impl.pane

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearchStore
import javax.inject.Inject

/**
 * Presenter for the Search tab's second pane.
 *
 * The mode is a projection of [SubmittedSearchStore.submitted]: [SearchPaneMode.Explore]
 * with no search, [SearchPaneMode.Summary] with one. The pane is composed in the list-detail
 * scene's placeholder slot, so this ViewModel is scoped to the logged-in shell entry and
 * survives tab switches and fold changes; it holds what `rememberSaveable` cannot keep there
 * (scroll positions, "Show more" toggles).
 *
 * The Summary's scroll position starts over whenever the searched query or filters change;
 * Explore's is kept across searches. Changing only the result tab keeps both.
 */
@HiltViewModel
internal class SearchPaneViewModel
    @Inject
    constructor(
        store: SubmittedSearchStore,
    ) : MviViewModel<SearchPaneState, SearchPaneEvent, SearchPaneEffect>(SearchPaneState()) {
        init {
            store.submitted
                .onEach { search -> onSubmittedChanged(search) }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: SearchPaneEvent) {
            when (event) {
                is SearchPaneEvent.ScrollSaved -> onScrollSaved(event)
                SearchPaneEvent.FeedsExpansionToggled -> setState { copy(feedsExpanded = !feedsExpanded) }
                SearchPaneEvent.AccountsExpansionToggled -> setState { copy(accountsExpanded = !accountsExpanded) }
            }
        }

        private fun onSubmittedChanged(search: SubmittedSearch?) {
            val key = search?.let { SummaryKey(it.query, it.filters) }
            setState {
                copy(
                    mode = if (search == null) SearchPaneMode.Explore else SearchPaneMode.Summary(search),
                    summaryScroll = if (key != summaryKey) ScrollPosition() else summaryScroll,
                    summaryKey = key,
                )
            }
        }

        private fun onScrollSaved(event: SearchPaneEvent.ScrollSaved) {
            setState {
                if (event.target == ScrollTarget.Summary && event.summaryKey != null && event.summaryKey != summaryKey) {
                    return@setState this
                }
                val target =
                    event.target
                        ?: if (mode is SearchPaneMode.Summary) ScrollTarget.Summary else ScrollTarget.Explore
                when (target) {
                    ScrollTarget.Explore -> copy(exploreScroll = event.position)
                    ScrollTarget.Summary -> copy(summaryScroll = event.position)
                }
            }
        }
    }
