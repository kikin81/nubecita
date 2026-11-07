package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import kotlin.time.Instant

/**
 * Detent position for the post comments sheet.
 */
enum class PostCommentsDetent {
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
sealed interface PostCommentsStatus {
    data object Idle : PostCommentsStatus

    data object Loading : PostCommentsStatus

    data object Empty : PostCommentsStatus

    data class Loaded(
        val comments: ImmutableList<CommentItemUi>,
    ) : PostCommentsStatus

    data class Error(
        val message: String? = null,
        val cause: Throwable? = null,
    ) : PostCommentsStatus
}

/**
 * MVI state for the adaptive post comments sheet.
 */
@Immutable
data class PostCommentsState(
    val postUri: String? = null,
    val detent: PostCommentsDetent = PostCommentsDetent.Hidden,
    val status: PostCommentsStatus = PostCommentsStatus.Idle,
    val comments: ImmutableList<CommentItemUi> = persistentListOf(),
    val isRefreshing: Boolean = false,
) : UiState {
    val isVisible: Boolean get() = detent.isVisible
    val isEmpty: Boolean get() = status is PostCommentsStatus.Empty || (status is PostCommentsStatus.Loaded && comments.isEmpty())
    val isLoading: Boolean get() = status is PostCommentsStatus.Loading
    val isError: Boolean get() = status is PostCommentsStatus.Error
}

/**
 * UI events/intents dispatched to [PostCommentsViewModel].
 */
sealed interface PostCommentsEvent : UiEvent {
    data class OpenComments(
        val postUri: String,
        val initialDetent: PostCommentsDetent = PostCommentsDetent.PartiallyExpanded,
    ) : PostCommentsEvent

    data object DismissComments : PostCommentsEvent

    data class SetDetent(
        val detent: PostCommentsDetent,
    ) : PostCommentsEvent

    data class LikeComment(
        val commentId: String,
    ) : PostCommentsEvent

    data object RetryLoad : PostCommentsEvent

    data object Refresh : PostCommentsEvent

    data object OpenComposer : PostCommentsEvent
}

/**
 * One-shot side effects emitted by [PostCommentsViewModel].
 */
sealed interface PostCommentsEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : PostCommentsEffect

    data class NavigateToComposer(
        val parentPostUri: String,
    ) : PostCommentsEffect
}
