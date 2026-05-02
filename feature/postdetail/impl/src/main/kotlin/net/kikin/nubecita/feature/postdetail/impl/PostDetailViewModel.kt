package net.kikin.nubecita.feature.postdetail.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.postdetail.impl.data.PostThreadRepository
import java.io.IOException

/**
 * Presenter for the post-detail screen.
 *
 * Uses Hilt's assisted-injection bridge so the [PostDetailRoute] (the
 * Nav 3 NavKey carrying the focus URI) flows from the entry-provider
 * call site into the VM constructor without a SavedStateHandle decode
 * step. The canonical Nav 3 pattern documented in the official Hilt
 * recipe — `hiltViewModel<VM, Factory>(creationCallback = { it.create(key) })`
 * — preserves a per-NavEntry VM instance via the
 * `rememberViewModelStoreNavEntryDecorator` already wired in `MainShell`.
 */
@HiltViewModel(assistedFactory = PostDetailViewModel.Factory::class)
internal class PostDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: PostDetailRoute,
        private val postThreadRepository: PostThreadRepository,
    ) : MviViewModel<PostDetailState, PostDetailEvent, PostDetailEffect>(PostDetailState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: PostDetailRoute): PostDetailViewModel
        }

        override fun handleEvent(event: PostDetailEvent) {
            when (event) {
                PostDetailEvent.Load -> load()
                PostDetailEvent.Refresh -> refresh()
                PostDetailEvent.Retry -> load()
                is PostDetailEvent.OnPostTapped -> sendEffect(PostDetailEffect.NavigateToPost(event.postUri))
                is PostDetailEvent.OnAuthorTapped -> sendEffect(PostDetailEffect.NavigateToAuthor(event.authorDid))
            }
        }

        private fun load() {
            // Allow initial load (from Idle, the default) and Retry from
            // InitialError. Any other status (InitialLoading, Refreshing)
            // means a fetch is already in flight — second Load is a no-op
            // so the UI can dispatch on every recomposition without N
            // concurrent fetches racing on setState.
            val status = uiState.value.loadStatus
            if (status != PostDetailLoadStatus.Idle && status !is PostDetailLoadStatus.InitialError) return
            setState { copy(loadStatus = PostDetailLoadStatus.InitialLoading) }
            viewModelScope.launch {
                postThreadRepository
                    .getPostThread(uri = route.postUri)
                    .onSuccess { items ->
                        // An empty list means the lexicon surface degraded the
                        // focus to either an Unknown open-union variant OR a
                        // record that failed to decode. Both surface as
                        // NotFound to the user — there's nothing renderable.
                        if (items.isEmpty()) {
                            setState {
                                copy(loadStatus = PostDetailLoadStatus.InitialError(PostDetailError.NotFound))
                            }
                        } else {
                            setState { copy(items = items, loadStatus = PostDetailLoadStatus.Idle) }
                        }
                    }.onFailure { throwable ->
                        setState {
                            copy(loadStatus = PostDetailLoadStatus.InitialError(throwable.toPostDetailError()))
                        }
                    }
            }
        }

        private fun refresh() {
            // Mutually exclusive with initial-load: dispatching Refresh
            // while another load is in flight is a no-op (drop the event
            // rather than queue, so back-pressure on a flapping
            // pull-to-refresh gesture stays at the user's wrist).
            if (uiState.value.loadStatus != PostDetailLoadStatus.Idle) return
            setState { copy(loadStatus = PostDetailLoadStatus.Refreshing) }
            viewModelScope.launch {
                postThreadRepository
                    .getPostThread(uri = route.postUri)
                    .onSuccess { items ->
                        setState {
                            // A refresh that returns an empty list means the
                            // post became unavailable since the previous fetch.
                            // Preserve the prior items and stay on Idle — the
                            // user can keep reading what they had.
                            if (items.isEmpty()) {
                                copy(loadStatus = PostDetailLoadStatus.Idle)
                            } else {
                                copy(items = items, loadStatus = PostDetailLoadStatus.Idle)
                            }
                        }
                    }.onFailure { throwable ->
                        // Preserve items on refresh failure; surface as a snackbar.
                        setState { copy(loadStatus = PostDetailLoadStatus.Idle) }
                        sendEffect(PostDetailEffect.ShowError(throwable.toPostDetailError()))
                    }
            }
        }

        private fun Throwable.toPostDetailError(): PostDetailError =
            when (this) {
                is NoSessionException -> PostDetailError.Unauthenticated
                is IOException -> PostDetailError.Network
                is XrpcError -> {
                    // The lexicon declares `NotFound` as the only typed error
                    // for getPostThread; the runtime's open-union mapper also
                    // routes any 4xx whose body says `error: "NotFound"` to
                    // an XrpcError with `errorName == "NotFound"`. Either
                    // shape collapses to the same UI surface.
                    if (errorName.equals("NotFound", ignoreCase = true) || status == HTTP_NOT_FOUND) {
                        PostDetailError.NotFound
                    } else {
                        PostDetailError.Unknown(cause = errorName)
                    }
                }
                else -> PostDetailError.Unknown(cause = message)
            }

        private companion object {
            const val HTTP_NOT_FOUND = 404
        }
    }
