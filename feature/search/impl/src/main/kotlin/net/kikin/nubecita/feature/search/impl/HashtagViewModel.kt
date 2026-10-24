package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.feature.search.api.Hashtag
import net.kikin.nubecita.feature.search.impl.data.SearchPostsPage
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import java.net.URLEncoder

/**
 * Presenter for the Hashtag screen: two independent sort tabs (Top / Latest)
 * over [SearchPostsRepository.searchHashtag], plus an optional author filter.
 *
 * - A tab loads on first selection (Top on init) and keeps its results when
 *   the user switches away.
 * - Every request captures the [epoch] it was issued in. Toggling the author
 *   filter bumps the epoch and resets both tabs, so a response for the old
 *   filter can never land on the new one.
 * - Post interactions delegate to [PostInteractionHandler], and the shared
 *   [PostInteractionsCache] is mirrored into both tabs (same pattern as
 *   [SearchPostsViewModel]).
 */
@HiltViewModel(assistedFactory = HashtagViewModel.Factory::class)
internal class HashtagViewModel
    @AssistedInject
    constructor(
        @Assisted route: Hashtag,
        private val repository: SearchPostsRepository,
        private val cache: PostInteractionsCache,
        private val handler: PostInteractionHandler,
    ) : MviViewModel<HashtagState, HashtagEvent, HashtagEffect>(HashtagState(tag = route.tag, author = route.author)),
        PostInteractionHandler by handler {
        @AssistedFactory
        interface Factory {
            fun create(route: Hashtag): HashtagViewModel
        }

        private var epoch = 0
        private val firstPageJobs = mutableMapOf<SearchPostsSort, Job>()

        init {
            handler.bind(PostSurface.Search, viewModelScope)

            viewModelScope.launch {
                cache.state.collect { snapshot ->
                    setState {
                        SearchPostsSort.entries.fold(this) { state, sort ->
                            val tab = state.tab(sort)
                            val loaded = tab.status as? SearchPostsLoadStatus.Loaded ?: return@fold state
                            state.withTab(sort, tab.copy(status = loaded.copy(items = loaded.items.applyInteractions(snapshot))))
                        }
                    }
                }
            }

            loadFirstPage(SearchPostsSort.TOP)
        }

        override fun handleEvent(event: HashtagEvent) {
            when (event) {
                is HashtagEvent.TabSelected -> selectTab(event.sort)
                HashtagEvent.AuthorFilterToggled -> toggleAuthorFilter()
                HashtagEvent.LoadMore -> loadMore(uiState.value.selectedSort)
                HashtagEvent.Refresh -> refresh(uiState.value.selectedSort)
                HashtagEvent.Retry -> loadFirstPage(uiState.value.selectedSort)
                HashtagEvent.ShareClicked -> sendEffect(HashtagEffect.Share(shareUrl()))
                is HashtagEvent.PostTapped -> sendEffect(HashtagEffect.NavigateToPost(event.uri))
                is HashtagEvent.OnAuthorTapped -> sendEffect(HashtagEffect.NavigateToProfile(event.actor))
                is HashtagEvent.TagTapped -> sendEffect(HashtagEffect.NavigateToHashtag(event.tag, event.author))
            }
        }

        private fun selectTab(sort: SearchPostsSort) {
            setState { copy(selectedSort = sort) }
            if (uiState.value.tab(sort).status == SearchPostsLoadStatus.Idle) loadFirstPage(sort)
        }

        private fun toggleAuthorFilter() {
            if (uiState.value.author == null) return
            epoch++
            firstPageJobs.values.forEach(Job::cancel)
            firstPageJobs.clear()
            setState { copy(authorFilterOn = !authorFilterOn, top = HashtagTabState(), latest = HashtagTabState()) }
            loadFirstPage(uiState.value.selectedSort)
        }

        private fun loadFirstPage(sort: SearchPostsSort) {
            firstPageJobs.remove(sort)?.cancel()
            setState { withTab(sort, HashtagTabState(status = SearchPostsLoadStatus.InitialLoading)) }
            val requestEpoch = epoch
            firstPageJobs[sort] =
                viewModelScope.launch {
                    fetch(sort, cursor = null)
                        .onSuccess { page ->
                            if (requestEpoch != epoch) return@onSuccess
                            setState { withTab(sort, HashtagTabState(status = firstPageStatus(page))) }
                        }.onFailure { throwable ->
                            if (requestEpoch != epoch) return@onFailure
                            setState {
                                withTab(sort, HashtagTabState(status = SearchPostsLoadStatus.InitialError(throwable.toSearchPostsError())))
                            }
                        }
                }
        }

        private fun refresh(sort: SearchPostsSort) {
            val tab = uiState.value.tab(sort)
            // Nothing on screen to refresh yet: an in-flight first load or an
            // error/idle tab goes through the first-page path instead.
            if (tab.isRefreshing || tab.status == SearchPostsLoadStatus.InitialLoading) return
            // An append in flight would splice the old next page onto the fresh list.
            if ((tab.status as? SearchPostsLoadStatus.Loaded)?.isAppending == true) return
            if (tab.status !is SearchPostsLoadStatus.Loaded && tab.status != SearchPostsLoadStatus.Empty) {
                loadFirstPage(sort)
                return
            }
            setState { withTab(sort, tab.copy(isRefreshing = true)) }
            val requestEpoch = epoch
            viewModelScope.launch {
                fetch(sort, cursor = null)
                    .onSuccess { page ->
                        if (requestEpoch != epoch) return@onSuccess
                        setState { withTab(sort, HashtagTabState(status = firstPageStatus(page))) }
                    }.onFailure { throwable ->
                        if (requestEpoch != epoch) return@onFailure
                        setState { withTab(sort, tab(sort).copy(isRefreshing = false)) }
                        sendEffect(HashtagEffect.ShowError(throwable.toSearchPostsError()))
                    }
            }
        }

        private fun loadMore(sort: SearchPostsSort) {
            val tab = uiState.value.tab(sort)
            val status = tab.status as? SearchPostsLoadStatus.Loaded ?: return
            if (status.endReached || status.isAppending || tab.isRefreshing) return

            setState { withTab(sort, tab.copy(status = status.copy(isAppending = true))) }
            val requestEpoch = epoch
            viewModelScope.launch {
                fetch(sort, cursor = status.nextCursor)
                    .onSuccess { page ->
                        if (requestEpoch != epoch) return@onSuccess
                        cache.seed(page.items.map { it.post })
                        setState {
                            val current = tab(sort)
                            val loaded = current.status as? SearchPostsLoadStatus.Loaded ?: return@setState this
                            // Cursor pages can re-include posts; duplicate LazyColumn
                            // keys crash, so existing items win and order stays stable.
                            val appended =
                                (loaded.items + page.items)
                                    .distinctBy { it.post.id }
                                    .toImmutableList()
                                    .applyInteractions(cache.state.value)
                            withTab(
                                sort,
                                current.copy(
                                    status =
                                        loaded.copy(
                                            items = appended,
                                            nextCursor = page.nextCursor,
                                            endReached = page.nextCursor == null,
                                            isAppending = false,
                                        ),
                                ),
                            )
                        }
                    }.onFailure { throwable ->
                        if (requestEpoch != epoch) return@onFailure
                        setState {
                            val current = tab(sort)
                            val loaded = current.status as? SearchPostsLoadStatus.Loaded ?: return@setState this
                            withTab(sort, current.copy(status = loaded.copy(isAppending = false)))
                        }
                        sendEffect(HashtagEffect.ShowError(throwable.toSearchPostsError()))
                    }
            }
        }

        private suspend fun fetch(
            sort: SearchPostsSort,
            cursor: String?,
        ): Result<SearchPostsPage> {
            val state = uiState.value
            return repository.searchHashtag(
                tag = state.tag,
                author = state.author?.takeIf { state.authorFilterOn },
                cursor = cursor,
                sort = sort,
            )
        }

        private fun firstPageStatus(page: SearchPostsPage): SearchPostsLoadStatus {
            cache.seed(page.items.map { it.post })
            val items =
                page.items
                    .distinctBy { it.post.id }
                    .toImmutableList()
                    .applyInteractions(cache.state.value)
            return if (items.isEmpty()) {
                SearchPostsLoadStatus.Empty
            } else {
                SearchPostsLoadStatus.Loaded(items = items, nextCursor = page.nextCursor, endReached = page.nextCursor == null)
            }
        }

        private fun shareUrl(): String {
            val state = uiState.value
            val base = "https://bsky.app/hashtag/" + encode(state.tag)
            val author = state.author?.takeIf { state.authorFilterOn } ?: return base
            return "$base?author=" + encode(author)
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
