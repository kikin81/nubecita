package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.haptic.PostHaptics
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.postinteractions.ui.R
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.designsystem.component.NubecitaPrimaryButton
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Top header row for the video comments sheet displaying comment count and
 * an indeterminate progress indicator during active background revalidation.
 */
@Composable
fun VideoCommentsHeader(
    commentCount: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text =
                if (commentCount > 0) {
                    stringResource(R.string.video_comments_header_with_count, commentCount)
                } else {
                    stringResource(R.string.video_comments_header_title)
                },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isLoading) {
            NubecitaWavyProgressIndicator(
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Centered empty-state banner shown when a video has zero replies.
 */
@Composable
fun VideoCommentsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.ChatBubble,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    opticalSize = 28.dp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.video_comments_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.video_comments_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Centered error banner with retry button shown when initial thread fetching fails.
 */
@Composable
fun VideoCommentsErrorState(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    opticalSize = 28.dp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.video_comments_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            NubecitaPrimaryButton(
                onClick = onRetry,
                text = stringResource(R.string.video_comments_retry),
            )
        }
    }
}

/**
 * Loading state spinner displayed during the initial comments fetch.
 */
@Composable
fun VideoCommentsLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NubecitaWavyProgressIndicator(
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.video_comments_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Primary comments list composable in `:core:post-interactions-ui`.
 *
 * Handles loaded (with SWR pull-to-refresh), empty, error, and loading states.
 */
@Composable
fun VideoCommentsList(
    state: VideoCommentsState,
    onLikeClick: (CommentItemUi) -> Unit,
    onRetryClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthorClick: ((AuthorUi) -> Unit)? = null,
    onFacetTap: ((FacetTarget) -> Unit)? = null,
    onCommentClick: ((CommentItemUi) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 72.dp),
    lazyListState: LazyListState = rememberLazyListState(),
    haptics: PostHaptics = rememberPostHaptics(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        VideoCommentsHeader(
            commentCount = state.comments.size,
            isLoading = state.isRefreshing,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        when {
            state.isLoading && state.comments.isEmpty() -> {
                VideoCommentsLoadingState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            state.isError && state.comments.isEmpty() -> {
                val errorMessage = (state.status as? VideoCommentsStatus.Error)?.message
                VideoCommentsErrorState(
                    message = errorMessage,
                    onRetry = onRetryClick,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            state.isEmpty -> {
                VideoCommentsEmptyState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            else -> {
                NubecitaPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize().testTag(VideoCommentsTestTags.COMMENTS_LIST),
                    ) {
                        items(
                            items = state.comments,
                            key = { it.id },
                        ) { comment ->
                            CommentCard(
                                comment = comment,
                                onLikeClick = onLikeClick,
                                onAuthorClick = onAuthorClick,
                                onFacetTap = onFacetTap,
                                onCommentClick = onCommentClick,
                                haptics = haptics,
                            )
                        }
                    }
                }
            }
        }
    }
}
