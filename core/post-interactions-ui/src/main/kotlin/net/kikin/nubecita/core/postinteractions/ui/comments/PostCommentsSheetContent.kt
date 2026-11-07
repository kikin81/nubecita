package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.designsystem.adaptive.LocalSheetHiddenHeightPx

/**
 * Top-level content for the post comments sheet: [PostCommentsList] with a pinned
 * [DockedCommentPill] at the bottom.
 */
@Composable
fun PostCommentsSheetContent(
    state: PostCommentsState,
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
        PostCommentsList(
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

        // Anchored to the bottom of the content, which a partially-expanded
        // bottom sheet lays out below the window — so pull it back up by
        // however much of the sheet is hidden. In any other host that value is
        // zero and this is a plain bottom alignment (nubecita-970o).
        val hiddenPx = LocalSheetHiddenHeightPx.current
        DockedCommentPill(
            onClick = onOpenComposer,
            userAvatarUrl = currentUserAvatarUrl,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(x = 0, y = -hiddenPx()) },
        )
    }
}
