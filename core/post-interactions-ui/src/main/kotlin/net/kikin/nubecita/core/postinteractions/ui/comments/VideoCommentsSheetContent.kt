package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.FacetTarget

/**
 * Top-level content for the video comments sheet: [VideoCommentsList] with a pinned
 * [DockedCommentPill] at the bottom.
 */
@Composable
fun VideoCommentsSheetContent(
    state: VideoCommentsState,
    onLikeClick: (CommentItemUi) -> Unit,
    onRetryClick: () -> Unit,
    onRefresh: () -> Unit,
    onOpenComposer: () -> Unit,
    modifier: Modifier = Modifier,
    currentUserAvatarUrl: String? = null,
    onAuthorClick: ((AuthorUi) -> Unit)? = null,
    onFacetTap: ((FacetTarget) -> Unit)? = null,
    onCommentClick: ((CommentItemUi) -> Unit)? = null,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        VideoCommentsList(
            state = state,
            onLikeClick = onLikeClick,
            onRetryClick = onRetryClick,
            onRefresh = onRefresh,
            onAuthorClick = onAuthorClick,
            onFacetTap = onFacetTap,
            onCommentClick = onCommentClick,
            contentPadding = PaddingValues(bottom = 80.dp),
            lazyListState = lazyListState,
            modifier = Modifier.fillMaxSize(),
        )

        DockedCommentPill(
            onClick = onOpenComposer,
            userAvatarUrl = currentUserAvatarUrl,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
