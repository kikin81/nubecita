package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.component.NubecitaOverlayIconButton
import net.kikin.nubecita.designsystem.component.NubecitaOverlayStatButton
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.semanticColors
import net.kikin.nubecita.designsystem.spacing

/**
 * The post behind the images: author, caption and actions, over a gradient that
 * keeps white text readable on a bright photo.
 *
 * The gradient isn't clickable, so touches outside the controls fall through to
 * the image for zoom, paging and swipe-down.
 */
@Composable
internal fun PostDetailsOverlay(
    post: PostUi,
    callbacks: PostCallbacks,
    onAuthorClick: () -> Unit,
    onCaptionClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onRepostClick: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        SCRIM_MIDPOINT to Color.Black.copy(alpha = SCRIM_MID_ALPHA),
                        1f to Color.Black.copy(alpha = SCRIM_END_ALPHA),
                    ),
                ).windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
            modifier =
                Modifier
                    .widthIn(max = MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacing.s4,
                        end = MaterialTheme.spacing.s4,
                        top = SCRIM_LEAD_IN,
                        bottom = MaterialTheme.spacing.s2,
                    ),
        ) {
            AuthorRow(post = post, onClick = onAuthorClick)
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = CAPTION_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_TOUCH_HEIGHT)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.mediaviewer_open_post),
                                onClick = onCaptionClick,
                            ),
                )
            }
            ActionRow(
                post = post,
                callbacks = callbacks,
                onCommentsClick = onCommentsClick,
                onRepostClick = onRepostClick,
                onInteraction = onInteraction,
            )
        }
    }
}

@Composable
private fun AuthorRow(
    post: PostUi,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        modifier =
            Modifier
                .heightIn(min = MIN_TOUCH_HEIGHT)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.mediaviewer_open_profile),
                    onClick = onClick,
                ),
    ) {
        NubecitaAsyncImage(
            model = post.author.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape),
        )
        Column {
            Text(
                text = post.author.displayName.ifBlank { post.author.handle },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${post.author.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = HANDLE_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** FlowRow, because large counts at a large font size would otherwise run off the edge. */
@Composable
private fun ActionRow(
    post: PostUi,
    callbacks: PostCallbacks,
    onCommentsClick: () -> Unit,
    onRepostClick: () -> Unit,
    onInteraction: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        NubecitaOverlayStatButton(
            icon = NubecitaIconName.ChatBubble,
            accessibilityLabel = stringResource(R.string.mediaviewer_action_reply),
            onClick = onCommentsClick,
            count = post.stats.replyCount.toLong(),
            inline = true,
        )
        NubecitaOverlayStatButton(
            icon = NubecitaIconName.Repeat,
            accessibilityLabel = stringResource(R.string.mediaviewer_action_repost),
            onClick = onRepostClick,
            count = post.stats.repostCount.toLong(),
            active = post.viewer.isRepostedByViewer,
            activeColor = MaterialTheme.semanticColors.repostAccent,
            inline = true,
        )
        NubecitaOverlayStatButton(
            icon = NubecitaIconName.Favorite,
            accessibilityLabel = stringResource(R.string.mediaviewer_action_like),
            onClick = {
                onInteraction()
                callbacks.onLike(post)
            },
            count = post.stats.likeCount.toLong(),
            active = post.viewer.isLikedByViewer,
            toggleable = true,
            activeColor = MaterialTheme.semanticColors.likeAccent,
            inline = true,
        )
        NubecitaOverlayIconButton(
            icon = NubecitaIconName.Bookmark,
            accessibilityLabel = stringResource(R.string.mediaviewer_action_bookmark),
            onClick = {
                onInteraction()
                callbacks.onBookmark(post)
            },
            active = post.viewer.isBookmarked,
            toggleable = true,
        )
        NubecitaOverlayIconButton(
            icon = NubecitaIconName.IosShare,
            accessibilityLabel = stringResource(R.string.mediaviewer_action_share),
            onClick = {
                onInteraction()
                callbacks.onShare(post)
            },
        )
    }
}

private const val CAPTION_MAX_LINES = 3
private const val HANDLE_ALPHA = 0.8f
private const val SCRIM_MIDPOINT = 0.35f
private const val SCRIM_MID_ALPHA = 0.4f
private const val SCRIM_END_ALPHA = 0.75f
private val SCRIM_LEAD_IN = 96.dp
private val AVATAR_SIZE = 40.dp
private val MIN_TOUCH_HEIGHT = 48.dp
private val MAX_WIDTH = 640.dp
