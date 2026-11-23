package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The result tabs of a submitted search. */
internal enum class SearchTab { POSTS, PEOPLE, FEEDS, PACKS }

/**
 * What was searched, and how the results are being narrowed.
 *
 * [submission] counts explicit submits across the whole process, so submitting
 * the same text again is a new search with a new cache key rather than a replay
 * of the cached first page.
 */
internal data class SubmittedSearch(
    val query: String,
    val fromRecent: Boolean,
    val filters: PostSearchFilters = PostSearchFilters(),
    val selectedTab: SearchTab = SearchTab.POSTS,
    val isFilterSheetOpen: Boolean = false,
    val submission: Int = 1,
)

/**
 * The one place the submitted search lives. The Search screen writes it; the
 * result tabs read it to know what to fetch; anything outside the Search
 * screen (such as a second pane) can observe it too, which is why it is a
 * singleton `StateFlow` and not ViewModel state.
 *
 * Every mutator is a no-op when nothing is submitted, so a late write from a
 * screen that is going away cannot resurrect a cleared search.
 */
@Singleton
internal class SubmittedSearchStore
    @Inject
    constructor() {
        private val _submitted = MutableStateFlow<SubmittedSearch?>(null)

        /** The current search, or null when nothing is submitted. */
        val submitted: StateFlow<SubmittedSearch?> = _submitted

        /** Never reset, so a [SubmittedSearch.submission] is not reused within a process. */
        private var lastSubmission = 0

        /**
         * Starts a new search for [query]. Filters and the selected tab of a
         * search already in progress carry over to the new one.
         */
        @Synchronized
        fun submit(
            query: String,
            fromRecent: Boolean,
        ) {
            lastSubmission += 1
            val current = _submitted.value
            _submitted.value =
                SubmittedSearch(
                    query = query,
                    fromRecent = fromRecent,
                    filters = current?.filters ?: PostSearchFilters(),
                    selectedTab = current?.selectedTab ?: SearchTab.POSTS,
                    isFilterSheetOpen = current?.isFilterSheetOpen ?: false,
                    submission = lastSubmission,
                )
        }

        /** Ends the search; the next [submit] starts from default filters. */
        @Synchronized
        fun clear() {
            _submitted.value = null
        }

        @Synchronized
        fun updateFilters(transform: (PostSearchFilters) -> PostSearchFilters) {
            val current = _submitted.value ?: return
            _submitted.value = current.copy(filters = transform(current.filters))
        }

        @Synchronized
        fun selectTab(tab: SearchTab) {
            val current = _submitted.value ?: return
            _submitted.value = current.copy(selectedTab = tab)
        }

        @Synchronized
        fun setFilterSheetOpen(open: Boolean) {
            val current = _submitted.value ?: return
            _submitted.value = current.copy(isFilterSheetOpen = open)
        }
    }
