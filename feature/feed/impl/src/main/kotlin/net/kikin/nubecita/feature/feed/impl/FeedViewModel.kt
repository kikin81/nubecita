package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
internal class FeedViewModel
    @Inject
    constructor(
        private val feedRepository: FeedRepository,
    ) : MviViewModel<FeedState, FeedEvent, FeedEffect>(FeedState()) {
        override fun handleEvent(event: FeedEvent) {
            when (event) {
                FeedEvent.Load -> load()
                FeedEvent.Refresh -> refresh()
                FeedEvent.LoadMore -> loadMore()
                FeedEvent.Retry -> load()
                FeedEvent.ClearError -> Unit
                is FeedEvent.OnPostTapped -> sendEffect(FeedEffect.NavigateToPost(event.post))
                is FeedEvent.OnAuthorTapped -> sendEffect(FeedEffect.NavigateToAuthor(event.authorDid))
                is FeedEvent.OnLikeClicked -> Unit
                is FeedEvent.OnRepostClicked -> Unit
                is FeedEvent.OnReplyClicked -> Unit
                is FeedEvent.OnShareClicked -> Unit
            }
        }

        private fun load() {
            // Allow initial load (from Idle, the default) and Retry from
            // InitialError. Any other status (InitialLoading, Refreshing,
            // Appending) means a fetch is already in flight — second Load
            // is a no-op so the UI can dispatch on every recomposition
            // without N concurrent fetches racing on setState.
            val status = uiState.value.loadStatus
            if (status != FeedLoadStatus.Idle && status !is FeedLoadStatus.InitialError) return
            setState { copy(loadStatus = FeedLoadStatus.InitialLoading) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = null)
                    .onSuccess { page -> applyInitialPage(page) }
                    .onFailure { throwable ->
                        setState {
                            copy(loadStatus = FeedLoadStatus.InitialError(throwable.toFeedError()))
                        }
                    }
            }
        }

        private fun refresh() {
            // Refresh and append/initial-load are mutually exclusive per the
            // mvi-foundation spec: dispatching Refresh while another load is
            // in flight is a no-op (drop the event rather than queue, so the
            // back-pressure on a flapping pull-to-refresh gesture stays at the
            // user's wrist).
            if (uiState.value.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Refreshing) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = null)
                    .onSuccess { page ->
                        // Replace head; cursor becomes the response cursor (may be null
                        // when the entire feed fits in one page).
                        setState {
                            copy(
                                posts = page.posts,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                                loadStatus = FeedLoadStatus.Idle,
                            )
                        }
                    }.onFailure { throwable ->
                        // Preserve posts on refresh failure; surface as a snackbar.
                        setState { copy(loadStatus = FeedLoadStatus.Idle) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun loadMore() {
            val current = uiState.value
            // End-of-feed: do nothing. Idempotent — repeat LoadMore once
            // endReached has flipped is a no-op.
            if (current.endReached) return
            // Mutually exclusive with refresh / initial-load: if any fetch is
            // already in flight, drop the LoadMore event. Prevents the
            // overlapping-fetch race where two getTimeline calls both update
            // posts + nextCursor and the last writer wins non-deterministically.
            if (current.loadStatus != FeedLoadStatus.Idle) return
            setState { copy(loadStatus = FeedLoadStatus.Appending) }
            viewModelScope.launch {
                feedRepository
                    .getTimeline(cursor = current.nextCursor)
                    .onSuccess { page ->
                        setState {
                            // De-dupe by id so a server returning a page that overlaps
                            // the current tail (rare but possible during cursor
                            // resyncs) doesn't show the same post twice.
                            val seen = posts.mapTo(HashSet()) { it.id }
                            val merged = (posts + page.posts.filter { seen.add(it.id) }).toImmutableList()
                            copy(
                                posts = merged,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                                loadStatus = FeedLoadStatus.Idle,
                            )
                        }
                    }.onFailure { throwable ->
                        // Preserve posts AND cursor on append failure so the user can
                        // retry from the same page boundary.
                        setState { copy(loadStatus = FeedLoadStatus.Idle) }
                        sendEffect(FeedEffect.ShowError(throwable.toFeedError()))
                    }
            }
        }

        private fun applyInitialPage(page: TimelinePage) {
            setState {
                copy(
                    posts = page.posts,
                    nextCursor = page.nextCursor,
                    endReached = page.nextCursor == null || page.posts.isEmpty(),
                    loadStatus = FeedLoadStatus.Idle,
                )
            }
        }

        private fun Throwable.toFeedError(): FeedError =
            when (this) {
                is NoSessionException -> FeedError.Unauthenticated
                is IOException -> FeedError.Network
                else -> FeedError.Unknown(cause = message)
            }
    }
