package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.haptic.PostHaptics
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.core.common.time.rememberRelativeTimeText
import net.kikin.nubecita.core.postinteractions.ui.R
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.VerificationBadge
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.component.rememberTappableBlueskyAnnotatedString
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.semanticColors

/**
 * Renders an individual comment card within the video comments sheet.
 *
 * Displays author avatar, display name, handle, relative timestamp, body text
 * (with interactive Bluesky link/mention facets), and an animated heart like toggle
 * with tactile haptic cues.
 */
@Composable
fun CommentCard(
    comment: CommentItemUi,
    onLikeClick: (CommentItemUi) -> Unit,
    modifier: Modifier = Modifier,
    onAuthorClick: ((AuthorUi) -> Unit)? = null,
    onFacetTap: ((FacetTarget) -> Unit)? = null,
    onCommentClick: ((CommentItemUi) -> Unit)? = null,
    haptics: PostHaptics = rememberPostHaptics(),
) {
    val indentDp = ((comment.depth - 1).coerceIn(0, 3) * 16).dp
    val timestamp by rememberRelativeTimeText(then = comment.createdAt)
    val displayName = comment.author.displayName.ifBlank { comment.author.handle }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onCommentClick != null) {
                        Modifier.clickable { onCommentClick(comment) }
                    } else {
                        Modifier
                    },
                ).padding(
                    start = 16.dp + indentDp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NubecitaAvatar(
            model = comment.author.avatarUrl,
            contentDescription = displayName,
            size = 36.dp,
            fallback = avatarFallbackFor(comment.author),
            modifier =
                if (onAuthorClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable { onAuthorClick(comment.author) }
                } else {
                    Modifier
                },
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        if (onAuthorClick != null) {
                            Modifier.clickable { onAuthorClick(comment.author) }
                        } else {
                            Modifier
                        },
                )
                if (comment.author.verifiedBadge != VerifiedBadge.None) {
                    VerificationBadge(badge = comment.author.verifiedBadge)
                }
                Text(
                    text = stringResource(R.string.video_comments_author_handle, comment.author.handle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = stringResource(R.string.video_comments_relative_time, timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val annotatedBody =
                rememberTappableBlueskyAnnotatedString(
                    text = comment.text,
                    facets = comment.post.facets,
                    onFacetTap = onFacetTap ?: {},
                )
            Text(
                text = annotatedBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        CommentLikeButton(
            isLiked = comment.isLiked,
            likeCount = comment.likeCount,
            onClick = { onLikeClick(comment) },
            haptics = haptics,
        )
    }
}

/**
 * Dedicated like button for comment rows with spring pop animation,
 * [Role.Switch] a11y toggle semantics, and tactile haptic feedback.
 */
@Composable
fun CommentLikeButton(
    isLiked: Boolean,
    likeCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: PostHaptics = rememberPostHaptics(),
) {
    val popScale = remember { Animatable(1f) }
    val firstFrame = remember { mutableStateOf(true) }

    LaunchedEffect(isLiked) {
        if (firstFrame.value) {
            firstFrame.value = false
            return@LaunchedEffect
        }
        if (isLiked) {
            popScale.animateTo(
                targetValue = 1.25f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
            )
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
            )
        }
    }

    val likeAccent = MaterialTheme.semanticColors.likeAccent
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tint by animateColorAsState(
        targetValue = if (isLiked) likeAccent else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "CommentLike-tint",
    )

    val handleToggle = {
        if (isLiked) {
            haptics.likeOff()
        } else {
            haptics.likeOn()
        }
        onClick()
    }

    val accessibilityLabel = stringResource(R.string.video_comments_like_action)

    Column(
        modifier =
            modifier
                .clip(CircleShape)
                .toggleable(
                    value = isLiked,
                    role = Role.Switch,
                    onValueChange = { handleToggle() },
                ).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Favorite,
            contentDescription = accessibilityLabel,
            filled = isLiked,
            tint = tint,
            opticalSize = 18.dp,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = popScale.value
                    scaleY = popScale.value
                },
        )
        if (likeCount > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            val countText = rememberCompactCount(count = likeCount)
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
