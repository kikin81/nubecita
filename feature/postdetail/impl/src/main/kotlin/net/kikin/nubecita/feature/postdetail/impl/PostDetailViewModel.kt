package net.kikin.nubecita.feature.postdetail.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.postdetail.impl.data.PostThreadRepository
import net.kikin.nubecita.feature.postdetail.impl.data.ThreadItem
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
        private val postInteractionsCache: PostInteractionsCache,
    ) : MviViewModel<PostDetailState, PostDetailEvent, PostDetailEffect>(PostDetailState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: PostDetailRoute): PostDetailViewModel
        }

        init {
            // Subscribe to the cache BEFORE the initial thread load so the
            // first emission from seed() is captured rather than dropped.
            viewModelScope.launch {
                postInteractionsCache.state
                    .map { interactionMap -> uiState.value.applyInteractions(interactionMap) }
                    .distinctUntilChanged()
                    .collect { merged -> setState { merged } }
            }
        }

        override fun handleEvent(event: PostDetailEvent) {
            when (event) {
                PostDetailEvent.Load -> load()
                PostDetailEvent.Refresh -> refresh()
                PostDetailEvent.Retry -> load()
                is PostDetailEvent.OnPostTapped -> sendEffect(PostDetailEffect.NavigateToPost(event.postUri))
                is PostDetailEvent.OnQuotedPostTapped ->
                    sendEffect(PostDetailEffect.NavigateToPost(event.quotedPostUri))
                is PostDetailEvent.OnAuthorTapped -> sendEffect(PostDetailEffect.NavigateToAuthor(event.authorDid))
                PostDetailEvent.OnReplyClicked -> {
                    // The composer's parent context is the focus post —
                    // route.postUri is the canonical focus URI passed in
                    // from the entry point, so we don't need to walk the
                    // items list to discover it. Drop silently while the
                    // initial load hasn't produced a Focus row (the FAB
                    // is composed in the loaded state, but the user could
                    // tap mid-refresh; emitting an effect with no Focus
                    // resolved would feel arbitrary).
                    val focusUri = uiState.value.items.firstOrNull { it is ThreadItem.Focus }
                    if (focusUri != null) {
                        sendEffect(PostDetailEffect.NavigateToComposer(parentPostUri = route.postUri))
                    }
                }
                is PostDetailEvent.OnFocusImageClicked ->
                    sendEffect(
                        PostDetailEffect.NavigateToMediaViewer(
                            postUri = route.postUri,
                            imageIndex = event.imageIndex,
                        ),
                    )
                is PostDetailEvent.OnLikeClicked ->
                    viewModelScope.launch {
                        postInteractionsCache
                            .toggleLike(event.post.id, event.post.cid)
                            .onFailure { sendEffect(PostDetailEffect.ShowError(it.toPostDetailError())) }
                    }
                is PostDetailEvent.OnRepostClicked ->
                    viewModelScope.launch {
                        postInteractionsCache
                            .toggleRepost(event.post.id, event.post.cid)
                            .onFailure { sendEffect(PostDetailEffect.ShowError(it.toPostDetailError())) }
                    }
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
                            // Seed the cache after the thread load resolves so
                            // any in-flight optimistic writes are preserved
                            // against appview eventual-consistency lag.
                            val postsToSeed =
                                items.mapNotNull { item ->
                                    when (item) {
                                        is ThreadItem.Ancestor -> item.post
                                        is ThreadItem.Focus -> item.post
                                        is ThreadItem.Reply -> item.post
                                        is ThreadItem.Blocked,
                                        is ThreadItem.NotFound,
                                        is ThreadItem.Fold,
                                        -> null
                                    }
                                }
                            postInteractionsCache.seed(postsToSeed)
                            val mergedItems = items.map { it.applyInteraction(postInteractionsCache.state.value) }.toImmutableList()
                            setState { copy(items = mergedItems, loadStatus = PostDetailLoadStatus.Idle) }
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
                        // A refresh that returns an empty list means the
                        // post became unavailable since the previous fetch.
                        // Preserve the prior items and stay on Idle — the
                        // user can keep reading what they had.
                        if (items.isEmpty()) {
                            setState { copy(loadStatus = PostDetailLoadStatus.Idle) }
                        } else {
                            val postsToSeed =
                                items.mapNotNull { item ->
                                    when (item) {
                                        is ThreadItem.Ancestor -> item.post
                                        is ThreadItem.Focus -> item.post
                                        is ThreadItem.Reply -> item.post
                                        is ThreadItem.Blocked,
                                        is ThreadItem.NotFound,
                                        is ThreadItem.Fold,
                                        -> null
                                    }
                                }
                            postInteractionsCache.seed(postsToSeed)
                            val mergedItems = items.map { it.applyInteraction(postInteractionsCache.state.value) }.toImmutableList()
                            setState { copy(items = mergedItems, loadStatus = PostDetailLoadStatus.Idle) }
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

// ---------- state-projection helpers ---------------------------------------

private fun PostDetailState.applyInteractions(
    map: PersistentMap<String, PostInteractionState>,
): PostDetailState =
    copy(
        items = items.map { item -> item.applyInteraction(map) }.toImmutableList(),
    )

private fun ThreadItem.applyInteraction(
    map: PersistentMap<String, PostInteractionState>,
): ThreadItem =
    when (this) {
        is ThreadItem.Ancestor -> {
            val state = map[post.id] ?: return this
            copy(post = post.mergeInteractionState(state))
        }
        is ThreadItem.Focus -> {
            val state = map[post.id] ?: return this
            copy(post = post.mergeInteractionState(state))
        }
        is ThreadItem.Reply -> {
            val state = map[post.id] ?: return this
            copy(post = post.mergeInteractionState(state))
        }
        // Blocked, NotFound, Fold carry no PostUi — pass through.
        is ThreadItem.Blocked,
        is ThreadItem.NotFound,
        is ThreadItem.Fold,
        -> this
    }
