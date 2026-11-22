package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.SearchPerform
import net.kikin.nubecita.core.analytics.SearchScope
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.search.impl.data.SearchFirstPages
import javax.inject.Inject

/**
 * Presenter for the Search People tab. Reactive on a
 * [MutableStateFlow] of [FetchKey] — the screen Composable's
 * `LaunchedEffect(parentState.currentQuery)` calls [setQuery] whenever
 * the parent [SearchViewModel] emits a new debounced query, and
 * [SearchActorsEvent.Retry] updates the key from inside [handleEvent].
 *
 * The init pipeline runs:
 *
 *   fetchKey
 *     .onEach { setState(currentQuery) }
 *     .mapLatest { key -> if (blank) reset to Idle else runFirstPage(key) }
 *     .launchIn(viewModelScope)
 *
 * `mapLatest` cancels the prior in-flight fetch on a new key. Retry
 * bumps an internal incarnation token so the pipeline fires even when
 * the query hasn't changed. The blank branch lives INSIDE `mapLatest`
 * (rather than upstream via `.filter`) so a blank emission also
 * cancels any in-flight `runFirstPage` and resets `loadStatus` to
 * `Idle` — without this, clearing the search field after a load would
 * leave the stale `Loaded` (or `InitialLoading`) state visible.
 *
 * Does NOT inject the parent [SearchViewModel] — Hilt-injecting
 * ViewModels into each other is a smell. The screen Composable is the
 * orchestration seam.
 *
 * `loadMore` carries a stale-completion guard: captures the current
 * [FetchKey] at start, verifies it's unchanged before applying the
 * append. Prevents a stale page-N completion from being spliced onto
 * a different query's `Loaded` list after the user typed past the
 * pagination boundary. Inherited from vrba.6's code-quality lesson.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchActorsViewModel
    @Inject
    constructor(
        private val repository: ActorRepository,
        private val firstPages: SearchFirstPages,
        private val followRepository: FollowRepository,
        private val analytics: AnalyticsClient,
    ) : MviViewModel<SearchActorsState, SearchActorsEvent, SearchActorsEffect>(SearchActorsState()) {
        private data class FetchKey(
            val query: String,
            /** Bumps on [SearchActorsEvent.Retry] to force a re-emit when query didn't change. */
            val incarnation: Int,
            /** Whether the query arrived via a recent-search tap (for search_perform). */
            val fromRecent: Boolean,
            /** The store's submission counter: an explicit re-submit of the same text is a new search. */
            val submission: Int,
        )

        /** In-flight follow/unfollow jobs keyed by DID — drops rapid concurrent taps. */
        private val activeFollowJobs = mutableMapOf<String, Job>()

        private val fetchKey = MutableStateFlow(FetchKey(query = "", incarnation = 0, fromRecent = false, submission = 0))

        init {
            fetchKey
                .onEach { key -> setState { copy(currentQuery = key.query) } }
                .mapLatest { key ->
                    if (key.query.isBlank()) {
                        // Reset to Idle on blank — covers initial state and
                        // the user-clears-the-field case. Inside mapLatest
                        // (not upstream `.filter`) so a blank emission ALSO
                        // cancels any in-flight `runFirstPage`, preventing
                        // a stale `Loaded` from landing after the reset.
                        setState { copy(loadStatus = SearchActorsLoadStatus.Idle) }
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

        override fun handleEvent(event: SearchActorsEvent) {
            when (event) {
                SearchActorsEvent.LoadMore -> loadMore()
                SearchActorsEvent.Retry ->
                    fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
                SearchActorsEvent.ClearQueryClicked ->
                    sendEffect(SearchActorsEffect.NavigateToClearQuery)
                is SearchActorsEvent.ActorTapped ->
                    sendEffect(SearchActorsEffect.NavigateToProfile(event.handle))
                is SearchActorsEvent.FollowTapped -> onFollowTapped(event.did)
            }
        }

        /**
         * Optimistic follow / unfollow, the same shape as `DiscoverViewModel`'s:
         * flip first, call [FollowRepository], and on failure flip back on the
         * CURRENT state (a targeted rollback, never a snapshot restore) and tell
         * the screen. Analytics fires on success only.
         */
        private fun onFollowTapped(did: String) {
            if (activeFollowJobs[did]?.isActive == true) return
            val actor = loadedItems().find { it.did == did } ?: return
            val priorFollowUri = actor.followUri

            if (actor.isFollowing) {
                // An account known to be followed without its record URI cannot be unfollowed.
                val followUri = priorFollowUri ?: return
                updateActor(did) { it.copy(isFollowing = false, followUri = null) }
                activeFollowJobs[did] =
                    viewModelScope.launch {
                        followRepository
                            .unfollow(followUri)
                            .onSuccess {
                                analytics.log(InteractActor(ActorAction.Unfollow, PostSurface.Search))
                            }.onFailure {
                                updateActor(did) { it.copy(isFollowing = true, followUri = followUri) }
                                sendEffect(SearchActorsEffect.FollowFailed(wasUnfollow = true))
                            }
                    }
            } else {
                updateActor(did) { it.copy(isFollowing = true) }
                activeFollowJobs[did] =
                    viewModelScope.launch {
                        followRepository
                            .follow(did)
                            .onSuccess { newFollowUri ->
                                updateActor(did) { it.copy(isFollowing = true, followUri = newFollowUri) }
                                analytics.log(InteractActor(ActorAction.Follow, PostSurface.Search))
                            }.onFailure {
                                updateActor(did) { it.copy(isFollowing = false, followUri = null) }
                                sendEffect(SearchActorsEffect.FollowFailed(wasUnfollow = false))
                            }
                    }
            }
        }

        private fun loadedItems(): List<ActorUi> = (uiState.value.loadStatus as? SearchActorsLoadStatus.Loaded)?.items.orEmpty()

        /** Rewrites one row of the loaded list on the CURRENT state; a no-op when the list moved on. */
        private fun updateActor(
            did: String,
            transform: (ActorUi) -> ActorUi,
        ) {
            setState {
                val status = loadStatus as? SearchActorsLoadStatus.Loaded ?: return@setState this
                copy(
                    loadStatus =
                        status.copy(
                            items = status.items.map { if (it.did == did) transform(it) else it }.toImmutableList(),
                        ),
                )
            }
        }

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchActorsLoadStatus.InitialLoading) }
            firstPages
                .people(query = key.query, submission = key.submission)
                .onSuccess { page ->
                    // search_perform fires on a completed People search (incl. empty);
                    // the query text is never attached.
                    analytics.log(SearchPerform(scope = SearchScope.People, fromRecent = key.fromRecent))
                    val nextStatus =
                        if (page.items.isEmpty()) {
                            SearchActorsLoadStatus.Empty
                        } else {
                            SearchActorsLoadStatus.Loaded(
                                items = page.items,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState {
                        copy(loadStatus = SearchActorsLoadStatus.InitialError(throwable.toSearchActorsError()))
                    }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is SearchActorsLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return

            setState { copy(loadStatus = status.copy(isAppending = true)) }
            val cursor = status.nextCursor
            val capturedKey = fetchKey.value
            viewModelScope.launch {
                repository
                    .searchActors(query = capturedKey.query, cursor = cursor)
                    .onSuccess { page ->
                        // Stale-completion guard: if the user typed past
                        // this fetch's boundary (or hit Retry) while
                        // it was in flight, the mapLatest pipeline has
                        // already moved the state on. Don't splice old-
                        // query results onto a new-query list.
                        if (fetchKey.value != capturedKey) return@onSuccess
                        val current = uiState.value.loadStatus as? SearchActorsLoadStatus.Loaded ?: return@onSuccess
                        val appended = (current.items + page.items).toImmutableList()
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
                        // Same stale guard for failures.
                        if (fetchKey.value != capturedKey) return@onFailure
                        val current = uiState.value.loadStatus as? SearchActorsLoadStatus.Loaded ?: return@onFailure
                        setState { copy(loadStatus = current.copy(isAppending = false)) }
                        sendEffect(SearchActorsEffect.ShowAppendError(throwable.toSearchActorsError()))
                    }
            }
        }
    }
