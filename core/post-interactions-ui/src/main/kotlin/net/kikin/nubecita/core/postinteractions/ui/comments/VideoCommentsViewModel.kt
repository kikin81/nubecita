package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.data.models.ThreadItem
import javax.inject.Inject

/**
 * Presenter / ViewModel for the video comments sheet.
 *
 * Responsibilities:
 * - Fetches thread replies via [PostThreadRepository.getPostThread].
 * - Projects replies into [CommentItemUi] and seeds [PostInteractionsCache].
 * - Reconciles SWR background fetches with optimistic user likes and current scroll state.
 * - Manages detents ([VideoCommentsDetent.Hidden], [VideoCommentsDetent.PartiallyExpanded], [VideoCommentsDetent.Expanded]).
 */
@HiltViewModel
class VideoCommentsViewModel
    @Inject
    constructor(
        private val postThreadRepository: PostThreadRepository,
        private val postInteractionsCache: PostInteractionsCache,
    ) : MviViewModel<VideoCommentsState, VideoCommentsEvent, VideoCommentsEffect>(VideoCommentsState()) {
        private var loadJob: Job? = null

        init {
            // Collect interactions cache updates (e.g. from optimistic likes) and reconcile into current comments list
            viewModelScope.launch {
                postInteractionsCache.state.collect { interactionMap ->
                    setState {
                        if (comments.isEmpty()) {
                            this
                        } else {
                            val updated =
                                comments
                                    .map { comment ->
                                        val interaction = interactionMap[comment.id]
                                        if (interaction != null) {
                                            val updatedPost = comment.post.mergeInteractionState(interaction)
                                            comment.copy(
                                                post = updatedPost,
                                                isLiked = updatedPost.viewer.isLikedByViewer,
                                                likeCount = updatedPost.stats.likeCount.toLong(),
                                            )
                                        } else {
                                            comment
                                        }
                                    }.toImmutableList()
                            copy(
                                comments = updated,
                                status =
                                    if (status is VideoCommentsStatus.Loaded) {
                                        VideoCommentsStatus.Loaded(updated)
                                    } else {
                                        status
                                    },
                            )
                        }
                    }
                }
            }
        }

        override fun handleEvent(event: VideoCommentsEvent) {
            when (event) {
                is VideoCommentsEvent.OpenComments -> {
                    val currentUri = uiState.value.postUri
                    if (currentUri == event.postUri && uiState.value.comments.isNotEmpty()) {
                        // SWR revalidation: keep current comments visible, expand sheet, fetch fresh in background
                        setState {
                            copy(
                                detent = event.initialDetent,
                                isRefreshing = true,
                            )
                        }
                        loadThread(event.postUri, isInitial = false)
                    } else {
                        setState {
                            copy(
                                postUri = event.postUri,
                                detent = event.initialDetent,
                                status = VideoCommentsStatus.Loading,
                                comments = persistentListOf(),
                                isRefreshing = false,
                            )
                        }
                        loadThread(event.postUri, isInitial = true)
                    }
                }

                VideoCommentsEvent.DismissComments -> {
                    setState { copy(detent = VideoCommentsDetent.Hidden) }
                }

                is VideoCommentsEvent.SetDetent -> {
                    setState { copy(detent = event.detent) }
                }

                is VideoCommentsEvent.LikeComment -> {
                    val comment = uiState.value.comments.find { it.id == event.commentId }
                    if (comment != null) {
                        viewModelScope.launch {
                            postInteractionsCache
                                .toggleLike(comment.id, comment.cid)
                                .onFailure { error ->
                                    sendEffect(
                                        VideoCommentsEffect.ShowError(
                                            error.message ?: "Failed to like comment",
                                        ),
                                    )
                                }
                        }
                    }
                }

                VideoCommentsEvent.RetryLoad -> {
                    val uri = uiState.value.postUri
                    if (uri != null) {
                        setState {
                            copy(
                                status = VideoCommentsStatus.Loading,
                                isRefreshing = false,
                            )
                        }
                        loadThread(uri, isInitial = true)
                    }
                }

                VideoCommentsEvent.Refresh -> {
                    val uri = uiState.value.postUri
                    if (uri != null) {
                        setState { copy(isRefreshing = true) }
                        loadThread(uri, isInitial = false)
                    }
                }

                VideoCommentsEvent.OpenComposer -> {
                    val uri = uiState.value.postUri
                    if (uri != null) {
                        sendEffect(VideoCommentsEffect.NavigateToComposer(uri))
                    }
                }

                is VideoCommentsEvent.OnComposerSubmitted -> {
                    val uri = uiState.value.postUri
                    if (uri != null && event.event.replyToUri == uri) {
                        setState { copy(isRefreshing = true) }
                        loadThread(uri, isInitial = false)
                    }
                }
            }
        }

        private fun loadThread(
            uri: String,
            isInitial: Boolean,
        ) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    postThreadRepository
                        .getPostThread(uri)
                        .onSuccess { items ->
                            val replies = items.filterIsInstance<ThreadItem.Reply>()
                            val commentItems = replies.map { it.toCommentItemUi() }

                            // Seed interactions cache with wire posts so like state is tracked
                            postInteractionsCache.seed(replies.map { it.post })

                            // Apply any existing optimistic interaction overrides from cache
                            val snapshot = postInteractionsCache.state.value
                            val reconciled =
                                commentItems
                                    .map { comment ->
                                        val interaction = snapshot[comment.id]
                                        if (interaction != null) {
                                            val updatedPost = comment.post.mergeInteractionState(interaction)
                                            comment.copy(
                                                post = updatedPost,
                                                isLiked = updatedPost.viewer.isLikedByViewer,
                                                likeCount = updatedPost.stats.likeCount.toLong(),
                                            )
                                        } else {
                                            comment
                                        }
                                    }.toImmutableList()

                            setState {
                                copy(
                                    comments = reconciled,
                                    status =
                                        if (reconciled.isEmpty()) {
                                            VideoCommentsStatus.Empty
                                        } else {
                                            VideoCommentsStatus.Loaded(reconciled)
                                        },
                                    isRefreshing = false,
                                )
                            }
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            val hasExistingComments = uiState.value.comments.isNotEmpty()
                            setState {
                                if (hasExistingComments) {
                                    copy(isRefreshing = false)
                                } else {
                                    copy(
                                        status =
                                            VideoCommentsStatus.Error(
                                                message = error.message ?: "Failed to load comments",
                                                cause = error,
                                            ),
                                        isRefreshing = false,
                                    )
                                }
                            }
                            if (hasExistingComments) {
                                sendEffect(
                                    VideoCommentsEffect.ShowError(
                                        error.message ?: "Failed to refresh comments",
                                    ),
                                )
                            }
                        }
                }
        }
    }
