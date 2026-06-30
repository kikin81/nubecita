package net.kikin.nubecita.feature.postdetail.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.MuteRepository
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
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
 *
 * Implements [PostInteractionHandler] via Kotlin `by` delegation onto
 * the injected [handler] (a [net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionHandler]
 * bound by Hilt). Like / repost / share calls from the screen Composable
 * reach the handler directly; [onOverflowAction] is overridden locally
 * to intercept MuteAuthor / UnmuteAuthor (optimistic flag-flip + rollback
 * on the thread items) before delegating all other actions to [handler].
 */
@HiltViewModel(assistedFactory = PostDetailViewModel.Factory::class)
internal class PostDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: PostDetailRoute,
        private val postThreadRepository: PostThreadRepository,
        private val muteRepository: MuteRepository,
        private val handler: PostInteractionHandler,
    ) : MviViewModel<PostDetailState, PostDetailEvent, PostDetailEffect>(PostDetailState()),
        PostInteractionHandler by handler {
        @AssistedFactory
        interface Factory {
            fun create(route: PostDetailRoute): PostDetailViewModel
        }

        init {
            handler.bind(PostSurface.PostDetail, viewModelScope)

            // Mirror handler tap-markers into PostDetailState so PostDetailScreenContent
            // can animate the ±1 count transition before the network resolves.
            viewModelScope.launch {
                handler.tapMarkers.collect { markers ->
                    setState {
                        copy(
                            lastLikeTapPostUri = markers.lastLikeTapPostUri,
                            lastRepostTapPostUri = markers.lastRepostTapPostUri,
                        )
                    }
                }
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
                    // The composer's parent context is the focus post. Gate on the
                    // same `showReplyFab` predicate the FAB uses: drop silently
                    // while no Focus is resolved (still loading) AND on reply-gated
                    // threads (the FAB is hidden, but defend the path in case a
                    // stale tap lands). Use the resolved focus post's *canonical*
                    // (DID-based) URI rather than `route.postUri` — the latter can
                    // be handle-based when post-detail was opened from a deep link
                    // (`PostDeepLinkKey.toPostDetailRoute` builds
                    // `at://<handle>/app.bsky.feed.post/<rkey>`), and a handle-based
                    // parent URI would break reply-ref resolution in the composer.
                    // `focusPost` is non-null whenever `showReplyFab` is true.
                    val focus = uiState.value.focusPost
                    if (uiState.value.showReplyFab && focus != null) {
                        sendEffect(PostDetailEffect.NavigateToComposer(parentPostUri = focus.id))
                    }
                }
                is PostDetailEvent.OnFocusImageClicked ->
                    // Use the focus post's canonical (DID-based) id, not
                    // route.postUri. When post-detail is opened from a deep link
                    // the route URI is handle-based (PostDeepLinkKey builds
                    // at://<handle>/...), and the media viewer's getPost(handle)
                    // 404s ("Post not found") because the appview's getPosts only
                    // resolves DID-based at-uris. The image tap only fires from the
                    // loaded focus post, so focusPost is non-null here; fall back to
                    // route.postUri defensively if it somehow isn't.
                    sendEffect(
                        PostDetailEffect.NavigateToMediaViewer(
                            postUri = uiState.value.focusPost?.id ?: route.postUri,
                            imageIndex = event.imageIndex,
                        ),
                    )
                is PostDetailEvent.OnVideoTapped ->
                    sendEffect(PostDetailEffect.NavigateToVideoPlayer(event.postUri))
                is PostDetailEvent.OnOverflowAction -> onOverflowAction(event.post, event.action)
            }
        }

        /**
         * Post-detail–specific override: [PostOverflowAction.MuteAuthor] /
         * [PostOverflowAction.UnmuteAuthor] carry optimistic flag-flip mutations
         * across all thread items authored by the same DID, with rollback on failure.
         * Unlike the feed / profile surfaces, posts in the thread are NOT removed —
         * the thread view keeps the muted author's posts visible but marked muted.
         * All other overflow actions are forwarded to [handler] which emits the
         * appropriate [net.kikin.nubecita.core.postinteractions.InteractionEffect]
         * onto the channel consumed by [net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions].
         */
        override fun onOverflowAction(
            post: PostUi,
            action: PostOverflowAction,
        ) {
            when (action) {
                PostOverflowAction.MuteAuthor -> {
                    val authorDid = post.author.did
                    setState { copy(items = items.updateMutedByAuthor(authorDid, muted = true)) }
                    viewModelScope.launch {
                        muteRepository
                            .muteActor(authorDid)
                            .onFailure {
                                setState { copy(items = items.updateMutedByAuthor(authorDid, muted = false)) }
                                sendEffect(PostDetailEffect.ShowError(it.toPostDetailError()))
                            }
                    }
                }
                PostOverflowAction.UnmuteAuthor -> {
                    val authorDid = post.author.did
                    setState { copy(items = items.updateMutedByAuthor(authorDid, muted = false)) }
                    viewModelScope.launch {
                        muteRepository
                            .unmuteActor(authorDid)
                            .onFailure {
                                setState { copy(items = items.updateMutedByAuthor(authorDid, muted = true)) }
                                sendEffect(PostDetailEffect.ShowError(it.toPostDetailError()))
                            }
                    }
                }
                else -> handler.onOverflowAction(post, action)
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
                            setState { copy(items = items.toImmutableList(), loadStatus = PostDetailLoadStatus.Idle) }
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
                            setState { copy(items = items.toImmutableList(), loadStatus = PostDetailLoadStatus.Idle) }
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

/**
 * Flip [ViewerStateUi.isAuthorMutedByViewer] to [muted] for every
 * [ThreadItem] whose post is authored by [did]. Items without a [PostUi]
 * (Blocked, NotFound, Fold) are passed through unchanged.
 *
 * Returns the same instance for items that are not authored by [did],
 * preserving reference equality so LazyColumn can skip recomposition
 * for unchanged items.
 */
private fun ImmutableList<ThreadItem>.updateMutedByAuthor(
    did: String,
    muted: Boolean,
): ImmutableList<ThreadItem> =
    map { item ->
        when (item) {
            is ThreadItem.Ancestor ->
                if (item.post.author.did == did) {
                    item.copy(post = item.post.copy(viewer = item.post.viewer.copy(isAuthorMutedByViewer = muted)))
                } else {
                    item
                }
            is ThreadItem.Focus ->
                if (item.post.author.did == did) {
                    item.copy(post = item.post.copy(viewer = item.post.viewer.copy(isAuthorMutedByViewer = muted)))
                } else {
                    item
                }
            is ThreadItem.Reply ->
                if (item.post.author.did == did) {
                    item.copy(post = item.post.copy(viewer = item.post.viewer.copy(isAuthorMutedByViewer = muted)))
                } else {
                    item
                }
            // Blocked, NotFound, Fold carry no PostUi — pass through.
            is ThreadItem.Blocked,
            is ThreadItem.NotFound,
            is ThreadItem.Fold,
            -> item
        }
    }.toImmutableList()
