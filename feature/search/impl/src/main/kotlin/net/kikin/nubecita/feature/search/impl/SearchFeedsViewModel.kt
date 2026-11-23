package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.FeedAction
import net.kikin.nubecita.core.analytics.FeedType
import net.kikin.nubecita.core.analytics.InteractFeed
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.SearchPerform
import net.kikin.nubecita.core.analytics.SearchScope
import net.kikin.nubecita.core.analytics.ViewFeed
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi
import net.kikin.nubecita.feature.search.impl.data.SearchFeedsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchFirstPages
import javax.inject.Inject

/**
 * Presenter for the Search Feeds tab. Reactive on a
 * [MutableStateFlow] of [FetchKey] — the screen Composable's
 * `LaunchedEffect(parentState.currentQuery)` calls [setQuery] whenever
 * the parent [SearchViewModel] emits a new debounced query.
 *
 * Shape mirrors [SearchActorsViewModel]: blank-handling lives inside
 * `mapLatest` so a blank emission cancels in-flight fetches and resets
 * `loadStatus` to `Idle`; [SearchFeedsEvent.Retry] bumps an incarnation
 * token so the pipeline fires even when the query hasn't changed;
 * `loadMore` carries a stale-completion guard against late page
 * completions after the user typed past the boundary.
 *
 * Does NOT inject the parent [SearchViewModel] — Hilt-injecting
 * ViewModels into each other is a smell. The screen Composable is the
 * orchestration seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchFeedsViewModel
    @Inject
    constructor(
        private val repository: SearchFeedsRepository,
        private val firstPages: SearchFirstPages,
        private val pinnedFeedsRepository: PinnedFeedsRepository,
        private val analytics: AnalyticsClient,
    ) : MviViewModel<SearchFeedsState, SearchFeedsEvent, SearchFeedsEffect>(SearchFeedsState()) {
        private data class FetchKey(
            val query: String,
            /** Bumps on [SearchFeedsEvent.Retry] to force a re-emit when query didn't change. */
            val incarnation: Int,
            /** Whether the query arrived via a recent-search tap (for search_perform). */
            val fromRecent: Boolean,
            /** The store's submission counter: an explicit re-submit of the same text is a new search. */
            val submission: Int,
        )

        /**
         * Latest pinned feed URIs from [PinnedFeedsRepository.observePinnedFeeds] — the same
         * source `DiscoverViewModel` seeds [FeedGeneratorUi.isPinned] from. Applied to every
         * page as it lands, and re-applied to the rows on screen whenever it changes, so a
         * pin made on another surface shows here without a request per row.
         */
        private var currentPinnedUris: Set<String> = emptySet()

        /** In-flight pin/unpin jobs keyed by feed URI — drops rapid concurrent taps. */
        private val activePinJobs = mutableMapOf<String, Job>()

        private val fetchKey = MutableStateFlow(FetchKey(query = "", incarnation = 0, fromRecent = false, submission = 0))

        init {
            pinnedFeedsRepository
                .observePinnedFeeds()
                .map { result -> result.feeds.map { it.uri }.toSet() }
                .onEach { uris ->
                    currentPinnedUris = uris
                    mapLoadedItems { it.withPinnedFrom(uris) }
                }.launchIn(viewModelScope)
            fetchKey
                .onEach { key -> setState { copy(currentQuery = key.query) } }
                .mapLatest { key ->
                    if (key.query.isBlank()) {
                        setState { copy(loadStatus = SearchFeedsLoadStatus.Idle) }
                    } else {
                        runFirstPage(key)
                    }
                }.launchIn(viewModelScope)
        }

        /** Called by the screen Composable from a `LaunchedEffect(parent.currentQuery)`. */
        fun setQuery(
            query: String,
            fromRecent: Boolean = false,
            submission: Int = 0,
        ) {
            fetchKey.update { it.copy(query = query, fromRecent = fromRecent, submission = submission) }
        }

        override fun handleEvent(event: SearchFeedsEvent) {
            when (event) {
                SearchFeedsEvent.LoadMore -> loadMore()
                SearchFeedsEvent.Retry ->
                    fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
                SearchFeedsEvent.ClearQueryClicked ->
                    sendEffect(SearchFeedsEffect.NavigateToClearQuery)
                is SearchFeedsEvent.FeedTapped ->
                    sendEffect(SearchFeedsEffect.NavigateToFeed(event.uri, event.displayName))
                is SearchFeedsEvent.PinTapped -> onPinTapped(event.uri)
            }
        }

        /**
         * Optimistic pin / unpin, the same shape as `DiscoverViewModel`'s: flip first, call
         * [PinnedFeedsRepository], and on failure flip back on the CURRENT state (a targeted
         * rollback, never a snapshot restore) and tell the screen. Analytics fires on success only.
         */
        private fun onPinTapped(uri: String) {
            if (activePinJobs[uri]?.isActive == true) return
            val feed = (uiState.value.loadStatus as? SearchFeedsLoadStatus.Loaded)?.items?.find { it.uri == uri } ?: return
            val wasPinned = feed.isPinned

            setPinned(uri, !wasPinned)
            activePinJobs[uri] =
                viewModelScope.launch {
                    val result =
                        if (wasPinned) {
                            pinnedFeedsRepository.unpinFeed(uri)
                        } else {
                            pinnedFeedsRepository.pinFeed(uri)
                        }
                    result
                        .onSuccess {
                            analytics.log(
                                InteractFeed(
                                    action = if (wasPinned) FeedAction.Unpin else FeedAction.Pin,
                                    surface = PostSurface.Search,
                                ),
                            )
                        }.onFailure {
                            setPinned(uri, wasPinned)
                            sendEffect(SearchFeedsEffect.PinFailed(wasUnpin = wasPinned))
                        }
                }
        }

        private fun setPinned(
            uri: String,
            pinned: Boolean,
        ) = mapLoadedItems { feed -> if (feed.uri == uri) feed.copy(isPinned = pinned) else feed }

        /** Rewrites the loaded rows on the CURRENT state; a no-op when nothing is loaded. */
        private fun mapLoadedItems(transform: (FeedGeneratorUi) -> FeedGeneratorUi) {
            setState {
                val status = loadStatus as? SearchFeedsLoadStatus.Loaded ?: return@setState this
                copy(loadStatus = status.copy(items = status.items.map(transform).toImmutableList()))
            }
        }

        private fun FeedGeneratorUi.withPinnedFrom(uris: Set<String>): FeedGeneratorUi = if (isPinned == (uri in uris)) this else copy(isPinned = uri in uris)

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchFeedsLoadStatus.InitialLoading) }
            firstPages
                .feeds(query = key.query, submission = key.submission)
                .onSuccess { page ->
                    // view_feed fires when the Feeds-tab results load. These
                    // are custom generator feeds, so the bucket is Custom; the
                    // query text and per-feed URIs are never attached.
                    analytics.log(ViewFeed(FeedType.Custom))
                    // search_perform for the Feeds scope (incl. empty results).
                    analytics.log(SearchPerform(scope = SearchScope.Feeds, fromRecent = key.fromRecent))
                    val nextStatus =
                        if (page.items.isEmpty()) {
                            SearchFeedsLoadStatus.Empty
                        } else {
                            SearchFeedsLoadStatus.Loaded(
                                items = page.items.map { it.withPinnedFrom(currentPinnedUris) }.toImmutableList(),
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState {
                        copy(loadStatus = SearchFeedsLoadStatus.InitialError(throwable.toSearchFeedsError()))
                    }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is SearchFeedsLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return

            setState { copy(loadStatus = status.copy(isAppending = true)) }
            val cursor = status.nextCursor
            val capturedKey = fetchKey.value
            viewModelScope.launch {
                repository
                    .searchFeeds(query = capturedKey.query, cursor = cursor)
                    .onSuccess { page ->
                        if (fetchKey.value != capturedKey) return@onSuccess
                        val current = uiState.value.loadStatus as? SearchFeedsLoadStatus.Loaded ?: return@onSuccess
                        val appended = (current.items + page.items.map { it.withPinnedFrom(currentPinnedUris) }).toImmutableList()
                        setState {
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
                        if (fetchKey.value != capturedKey) return@onFailure
                        val current = uiState.value.loadStatus as? SearchFeedsLoadStatus.Loaded ?: return@onFailure
                        setState { copy(loadStatus = current.copy(isAppending = false)) }
                        sendEffect(SearchFeedsEffect.ShowAppendError(throwable.toSearchFeedsError()))
                    }
            }
        }
    }
