package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.SearchPerform
import net.kikin.nubecita.core.analytics.SearchScope
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.core.posttranslation.PostTranslations
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchFirstPages
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearchStore
import javax.inject.Inject

/**
 * Presenter for the Search Posts tab. Reactive on the submitted search: it
 * combines [SubmittedSearchStore.submitted] with an internal retry token into
 * a [FetchKey], so a new submit, a filter change and `Retry` each refetch,
 * while anything the Posts tab does not fetch by (the selected tab, the filter
 * sheet) does not.
 *
 * The init pipeline runs:
 *
 *   fetchKey
 *     .onEach { setState(currentQuery, sort) }
 *     .mapLatest { key -> if (blank) reset to Idle else runFirstPage(key) }
 *     .launchIn(viewModelScope)
 *
 * `combine` conflates and the key is a data class, so identical consecutive
 * keys are skipped by `distinctUntilChanged`. `mapLatest` cancels the prior
 * in-flight fetch on a new key — the canonical pattern from
 * `:feature:composer:impl`'s `ComposerViewModel` typeahead path. The blank
 * branch lives INSIDE `mapLatest` (rather than upstream via `.filter`) so a
 * cleared search also cancels any in-flight `runFirstPage` and resets
 * `loadStatus` to `Idle` — without this, ending the search after a load would
 * leave the stale `Loaded` (or `InitialLoading`) state visible.
 *
 * Does NOT inject the parent `SearchViewModel` — Hilt-injecting
 * ViewModels into each other is a smell. The store is the seam: the sort
 * toggle writes it ([SearchPostsEvent.SortClicked]) and this tab reads it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchPostsViewModel
    @Inject
    constructor(
        private val repository: SearchPostsRepository,
        private val firstPages: SearchFirstPages,
        private val submittedSearch: SubmittedSearchStore,
        private val analytics: AnalyticsClient,
        private val cache: PostInteractionsCache,
        private val handler: PostInteractionHandler,
        /**
         * The app-wide translation store, exposed for the screen's Compose
         * layer the way the feed and post detail do. The ViewModel never
         * reads or writes it: translation state is snapshot-backed and read
         * per card, not part of `UiState`.
         */
        val translations: PostTranslations,
    ) : MviViewModel<SearchPostsState, SearchPostsEvent, SearchPostsEffect>(SearchPostsState()),
        PostInteractionHandler by handler {
        private data class FetchKey(
            val query: String,
            val filters: PostSearchFilters,
            /** The store's submission counter: re-submitting the same text is a new search. */
            val submission: Int,
            /** Whether the query arrived via a recent-search tap (for search_perform). */
            val fromRecent: Boolean,
            /** Bumps on [SearchPostsEvent.Retry] to force a re-emit when the search didn't change. */
            val incarnation: Int,
        )

        /** Bumps on [SearchPostsEvent.Retry]. */
        private val incarnation = MutableStateFlow(0)

        private fun keyFor(
            submitted: SubmittedSearch?,
            incarnation: Int,
        ) = FetchKey(
            query = submitted?.query.orEmpty(),
            filters = submitted?.filters ?: PostSearchFilters(),
            submission = submitted?.submission ?: 0,
            fromRecent = submitted?.fromRecent ?: false,
            incarnation = incarnation,
        )

        /**
         * The key this ViewModel is showing: set by the pipeline as it starts
         * handling a key. Pagination and its stale guards compare against this,
         * never the store, which can be ahead of the pipeline.
         */
        private var activeKey: FetchKey? = null

        /** The store's key as of right now. [loadMore] consults it only to see whether a reload is pending. */
        private fun storeKey() = keyFor(submittedSearch.submitted.value, incarnation.value)

        init {
            // Bind the handler to the Search surface and this VM's scope.
            handler.bind(PostSurface.Search, viewModelScope)

            // Mirror cache state into status.items so a like/repost from
            // another surface (feed, post-detail) reflects on the rendered
            // search results without a re-fetch.
            //
            // Read + merge happen atomically inside setState so a concurrent
            // loadMore/sort-change that lands between the snapshot read and
            // the state write can never be clobbered. StateFlow deduplication
            // makes the distinctUntilChanged wrapper unnecessary.
            viewModelScope.launch {
                cache.state.collect { snapshot ->
                    setState {
                        val status = loadStatus as? SearchPostsLoadStatus.Loaded ?: return@setState this
                        copy(loadStatus = status.copy(items = status.items.applyInteractions(snapshot)))
                    }
                }
            }

            combine(submittedSearch.submitted, incarnation, ::keyFor)
                .distinctUntilChanged()
                .onEach { key ->
                    activeKey = key
                    setState {
                        copy(
                            currentQuery = key.query,
                            sort = key.filters.sort,
                            filters = key.filters,
                            // The old results belong to the previous key; drop them now so
                            // no pagination can run against them before the reload starts.
                            loadStatus =
                                if (key.query.isBlank()) SearchPostsLoadStatus.Idle else SearchPostsLoadStatus.InitialLoading,
                        )
                    }
                }.mapLatest { key ->
                    if (key.query.isBlank()) {
                        // Reset to Idle on blank — covers initial state and
                        // the user-clears-the-field case. Inside mapLatest
                        // (not upstream `.filter`) so a blank emission ALSO
                        // cancels any in-flight `runFirstPage`, preventing
                        // a stale `Loaded` from landing after the reset.
                        setState { copy(loadStatus = SearchPostsLoadStatus.Idle) }
                    } else {
                        runFirstPage(key)
                    }
                }.launchIn(viewModelScope)
        }

        override fun handleEvent(event: SearchPostsEvent) {
            when (event) {
                SearchPostsEvent.LoadMore -> loadMore()
                SearchPostsEvent.Retry -> incarnation.update { it + 1 }
                SearchPostsEvent.ClearQueryClicked ->
                    sendEffect(SearchPostsEffect.NavigateToClearQuery)
                is SearchPostsEvent.SortClicked ->
                    submittedSearch.updateFilters { it.copy(sort = event.sort) }
                is SearchPostsEvent.FiltersChanged ->
                    submittedSearch.updateFilters { event.filters }
                SearchPostsEvent.OpenFilterSheetClicked ->
                    submittedSearch.setFilterSheetOpen(true)
                is SearchPostsEvent.PostTapped ->
                    sendEffect(SearchPostsEffect.NavigateToPost(event.uri))
                is SearchPostsEvent.OnAuthorTapped ->
                    sendEffect(SearchPostsEffect.NavigateToProfile(event.handle))
                is SearchPostsEvent.TagTapped ->
                    sendEffect(SearchPostsEffect.NavigateToHashtag(event.tag, event.author))
            }
        }

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchPostsLoadStatus.InitialLoading) }
            firstPages
                .posts(query = key.query, filters = key.filters, submission = key.submission)
                .onSuccess { page ->
                    // search_perform fires on a completed first-page search (incl.
                    // empty results); scope tracks the active Top/Latest sort. The
                    // query text is never attached.
                    analytics.log(
                        SearchPerform(
                            scope = if (key.filters.sort == SearchPostsSort.TOP) SearchScope.Top else SearchScope.Latest,
                            fromRecent = key.fromRecent,
                        ),
                    )
                    cache.seed(page.items.map { it.post })
                    // Dedupe by post id (the AT-URI the Posts-tab LazyColumn keys on):
                    // a single searchPosts response can contain the same post twice,
                    // and duplicate LazyColumn keys crash Compose. Defensive mirror of
                    // the append-path dedupe in loadMore(). applyInteractions projects
                    // the live cache so a post liked/reposted on another surface shows
                    // its optimistic state immediately, not on the next cache emission.
                    val deduped =
                        page.items
                            .distinctBy { it.post.id }
                            .toImmutableList()
                            .applyInteractions(cache.state.value)
                    val nextStatus =
                        if (deduped.isEmpty()) {
                            SearchPostsLoadStatus.Empty
                        } else {
                            SearchPostsLoadStatus.Loaded(
                                items = deduped,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                                hitsTotal = page.hitsTotal,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState {
                        copy(loadStatus = SearchPostsLoadStatus.InitialError(throwable.toSearchPostsError()))
                    }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is SearchPostsLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return
            val capturedKey = activeKey ?: return
            // The store moved on and the pipeline has not caught up: it is about to
            // reload, so this cursor belongs to the previous search.
            if (storeKey() != capturedKey) return

            setState { copy(loadStatus = status.copy(isAppending = true)) }
            val cursor = status.nextCursor
            viewModelScope.launch {
                repository
                    .searchPosts(
                        query = capturedKey.query,
                        filters = capturedKey.filters,
                        cursor = cursor,
                    ).onSuccess { page ->
                        // Stale-completion guard: if the user changed sort or query
                        // (or hit Retry) while this append was in flight, the
                        // mapLatest pipeline has already moved the state on. Don't
                        // splice old-sort results onto a new-sort list.
                        if (activeKey != capturedKey) return@onSuccess
                        cache.seed(page.items.map { it.post })
                        setState {
                            val current = loadStatus as? SearchPostsLoadStatus.Loaded ?: return@setState this
                            // Read + append INSIDE setState so a concurrent cache merge
                            // can't be clobbered by a value computed from a stale read.
                            // Dedupe by post id (the AT-URI the Posts-tab LazyColumn keys
                            // on) — AT Proto searchPosts cursor pages overlap / re-include
                            // posts, and a duplicate key crashes the LazyColumn
                            // (Crashlytics 159e25351be7042db517f3af684684db). Existing items
                            // win so order stays stable; applyInteractions projects the live
                            // cache onto the newly appended posts immediately.
                            val appended =
                                (current.items + page.items)
                                    .distinctBy { it.post.id }
                                    .toImmutableList()
                                    .applyInteractions(cache.state.value)
                            copy(
                                loadStatus =
                                    current.copy(
                                        items = appended,
                                        nextCursor = page.nextCursor,
                                        endReached = page.nextCursor == null,
                                        isAppending = false,
                                    ),
                            )
                        }
                    }.onFailure { throwable ->
                        // Same stale guard for failures — a snackbar for the OLD
                        // sort's append failure would be confusing once the user is
                        // already looking at LATEST results.
                        if (activeKey != capturedKey) return@onFailure
                        val current = uiState.value.loadStatus as? SearchPostsLoadStatus.Loaded ?: return@onFailure
                        setState { copy(loadStatus = current.copy(isAppending = false)) }
                        sendEffect(SearchPostsEffect.ShowAppendError(throwable.toSearchPostsError()))
                    }
            }
        }
    }

/**
 * Project a [PostInteractionsCache][net.kikin.nubecita.core.postinteractions.PostInteractionsCache]
 * snapshot onto the search-result item list. Replaces interaction fields
 * (like / repost counts and viewer flags) on every [FeedItemUi.Single]
 * whose post id appears in [interactionMap].
 *
 * Returns the same instance for any item whose post is absent from the
 * map, preserving reference equality so LazyColumn can skip recomposition
 * for unchanged items.
 */
internal fun ImmutableList<FeedItemUi.Single>.applyInteractions(
    interactionMap: PersistentMap<String, PostInteractionState>,
): ImmutableList<FeedItemUi.Single> =
    // One pass: deleted posts drop out (the flag is set only after the PDS
    // accepted the deletion, so there is nothing to undo), everything else
    // merges. Single lookup per item.
    mapNotNull { item ->
        val state = interactionMap[item.post.id] ?: return@mapNotNull item
        if (state.isDeleted) null else item.copy(post = item.post.mergeInteractionState(state))
    }.toImmutableList()
