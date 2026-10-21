package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.common.navigation.ComposerSubmitEvent
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import kotlin.time.Instant

/**
 * Detent position for the video comments sheet.
 */
enum class VideoCommentsDetent {
    Hidden,
    PartiallyExpanded,
    Expanded,
    ;

    val isVisible: Boolean get() = this != Hidden
}

/**
 * UI representation of an individual comment/reply item.
 */
@Immutable
data class CommentItemUi(
    val id: String,
    val cid: String,
    val author: AuthorUi,
    val text: String,
    val createdAt: Instant,
    val likeCount: Long,
    val isLiked: Boolean,
    val depth: Int = 1,
    val post: PostUi,
)

/**
 * Maps a thread reply item from the posts domain into a [CommentItemUi].
 */
fun ThreadItem.Reply.toCommentItemUi(): CommentItemUi =
    CommentItemUi(
        id = post.id,
        cid = post.cid,
        author = post.author,
        text = post.text,
        createdAt = post.createdAt,
        likeCount = post.stats.likeCount.toLong(),
        isLiked = post.viewer.isLikedByViewer,
        depth = depth,
        post = post,
    )

/**
 * Content load status of the comments list.
 */
@Immutable
sealed interface VideoCommentsStatus {
    data object Idle : VideoCommentsStatus

    data object Loading : VideoCommentsStatus

    data object Empty : VideoCommentsStatus

    data class Loaded(
        val comments: ImmutableList<CommentItemUi>,
    ) : VideoCommentsStatus

    data class Error(
        val message: String? = null,
        val cause: Throwable? = null,
    ) : VideoCommentsStatus
}

/**
 * MVI state for the adaptive video comments sheet.
 */
@Immutable
data class VideoCommentsState(
    val postUri: String? = null,
    val detent: VideoCommentsDetent = VideoCommentsDetent.Hidden,
    val status: VideoCommentsStatus = VideoCommentsStatus.Idle,
    val comments: ImmutableList<CommentItemUi> = persistentListOf(),
    val isRefreshing: Boolean = false,
) : UiState {
    val isVisible: Boolean get() = detent.isVisible
    val isEmpty: Boolean get() = status is VideoCommentsStatus.Empty || (status is VideoCommentsStatus.Loaded && comments.isEmpty())
    val isLoading: Boolean get() = status is VideoCommentsStatus.Loading
    val isError: Boolean get() = status is VideoCommentsStatus.Error
}

/**
 * UI events/intents dispatched to [VideoCommentsViewModel].
 */
sealed interface VideoCommentsEvent : UiEvent {
    data class OpenComments(
        val postUri: String,
        val initialDetent: VideoCommentsDetent = VideoCommentsDetent.PartiallyExpanded,
    ) : VideoCommentsEvent

    data object DismissComments : VideoCommentsEvent

    data class SetDetent(
        val detent: VideoCommentsDetent,
    ) : VideoCommentsEvent

    data class LikeComment(
        val commentId: String,
    ) : VideoCommentsEvent

    data object RetryLoad : VideoCommentsEvent

    data object Refresh : VideoCommentsEvent

    data object OpenComposer : VideoCommentsEvent

    data class OnComposerSubmitted(
        val event: ComposerSubmitEvent,
    ) : VideoCommentsEvent
}

/**
 * One-shot side effects emitted by [VideoCommentsViewModel].
 */
sealed interface VideoCommentsEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : VideoCommentsEffect

    data class NavigateToComposer(
        val parentPostUri: String,
    ) : VideoCommentsEffect
}
