package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.videos.impl.R
import net.kikin.nubecita.feature.videos.impl.VideoFeedTestTags

/**
 * Overlay chrome for one page of the vertical video feed (design D6): author and
 * caption bottom-left, action rail down the right edge, mute toggle at its foot.
 *
 * Stateless — it takes a [PostUi] and lambdas, holds no ViewModel and no player,
 * so it renders under layoutlib for screenshot tests.
 *
 * The bottom scrim is a gradient rather than a flat overlay: white text needs a
 * legibility floor over bright frames, but a full-screen scrim would dim the
 * video everywhere, which is the one thing this surface exists to show.
 */
@Composable
internal fun VideoPageChrome(
    post: PostUi,
    isMuted: Boolean,
    captionExpanded: Boolean,
    onCaptionToggle: () -> Unit,
    onAuthorTap: () -> Unit,
    onLike: () -> Unit,
    onRepost: () -> Unit,
    onReply: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    onOverflowAction: (PostOverflowAction) -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RAIL_GAP),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = RAIL_EDGE_PADDING),
        ) {
            VideoRailAction(
                icon = NubecitaIconName.Favorite,
                accessibilityLabel = stringResource(R.string.videos_action_like),
                onClick = onLike,
                count = post.stats.likeCount.toLong(),
                active = post.viewer.isLikedByViewer,
                toggleable = true,
                activeColor = MaterialTheme.colorScheme.secondary,
                testTag = VideoFeedTestTags.RAIL_LIKE,
            )
            VideoRailAction(
                icon = NubecitaIconName.Repeat,
                accessibilityLabel = stringResource(R.string.videos_action_repost),
                onClick = onRepost,
                count = post.stats.repostCount.toLong(),
                active = post.viewer.isRepostedByViewer,
                toggleable = true,
                activeColor = MaterialTheme.colorScheme.tertiary,
                testTag = VideoFeedTestTags.RAIL_REPOST,
            )
            VideoRailAction(
                icon = NubecitaIconName.Bookmark,
                accessibilityLabel = stringResource(R.string.videos_action_bookmark),
                onClick = onBookmark,
                active = post.viewer.isBookmarked,
                toggleable = true,
                activeColor = MaterialTheme.colorScheme.primary,
                testTag = VideoFeedTestTags.RAIL_BOOKMARK,
            )
            VideoRailAction(
                icon = NubecitaIconName.ChatBubble,
                accessibilityLabel = stringResource(R.string.videos_action_reply),
                onClick = onReply,
                count = post.stats.replyCount.toLong(),
                testTag = VideoFeedTestTags.RAIL_REPLY,
            )
            VideoRailAction(
                icon = NubecitaIconName.IosShare,
                accessibilityLabel = stringResource(R.string.videos_action_share),
                onClick = onShare,
                testTag = VideoFeedTestTags.RAIL_SHARE,
            )
            Box {
                // Keyed on post.id so the menu resets when this cell is bound to a
                // different post. The pager already keys pages on post.id, so today
                // this is redundant — but it keeps the reset guarantee local to the
                // cell rather than relying on an ancestor's key, and matches how the
                // caption's expand state is keyed.
                var overflowExpanded by remember(post.id) { mutableStateOf(false) }
                VideoRailAction(
                    icon = NubecitaIconName.MoreVert,
                    accessibilityLabel = stringResource(R.string.videos_action_more),
                    onClick = { overflowExpanded = true },
                    testTag = VideoFeedTestTags.RAIL_OVERFLOW,
                )
                VideoOverflowMenu(
                    post = post,
                    expanded = overflowExpanded,
                    onDismiss = { overflowExpanded = false },
                    onAction = onOverflowAction,
                )
            }
            VideoRailAction(
                icon = if (isMuted) NubecitaIconName.VolumeOff else NubecitaIconName.VolumeUp,
                accessibilityLabel = stringResource(R.string.videos_action_mute),
                onClick = onMuteToggle,
                active = isMuted,
                toggleable = true,
                testTag = VideoFeedTestTags.MUTE,
            )
        }
        // The scrim spans the FULL width so it fades out only vertically. Constraining
        // it to the text's width instead leaves a hard vertical seam down the middle
        // of the frame where the gradient stops.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA))),
                ).padding(BOTTOM_BLOCK_PADDING),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CAPTION_GAP),
                // Text stops short of the rail, which owns the right edge.
                modifier = Modifier.fillMaxWidth(BOTTOM_BLOCK_WIDTH_FRACTION),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AUTHOR_GAP),
                    modifier =
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.videos_open_profile),
                            onClick = onAuthorTap,
                        ),
                ) {
                    NubecitaAsyncImage(
                        model = post.author.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape),
                    )
                    Text(
                        text = post.author.displayName.ifBlank { post.author.handle },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (post.text.isNotBlank()) {
                    // Only offer the expand affordance when there is something to expand.
                    // A one-line caption that announces "double-tap to Expand caption" and
                    // then does nothing is a dead end for a TalkBack user.
                    //
                    // The `|| captionExpanded` is load-bearing: once expanded there is no
                    // visual overflow, so guarding on truncation alone would strip the
                    // affordance and leave no way to collapse again.
                    var isTruncated by remember(post.id) { mutableStateOf(false) }
                    val expandLabel = stringResource(R.string.videos_expand_caption)
                    val collapseLabel = stringResource(R.string.videos_collapse_caption)
                    Text(
                        text = post.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = if (captionExpanded) Int.MAX_VALUE else CAPTION_COLLAPSED_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result -> isTruncated = result.hasVisualOverflow },
                        modifier =
                            Modifier
                                .testTag(VideoFeedTestTags.CAPTION)
                                .then(
                                    if (isTruncated || captionExpanded) {
                                        Modifier.clickable(
                                            role = Role.Button,
                                            onClickLabel = if (captionExpanded) collapseLabel else expandLabel,
                                            onClick = onCaptionToggle,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
            }
        }
    }
}

private const val CAPTION_COLLAPSED_LINES = 2

/** The rail owns the right edge, so the text block stops short of it. */
private const val BOTTOM_BLOCK_WIDTH_FRACTION = 0.78f
private const val SCRIM_ALPHA = 0.55f
private val AVATAR_SIZE = 40.dp
private val RAIL_GAP = 12.dp
private val RAIL_EDGE_PADDING = 8.dp
private val CAPTION_GAP = 8.dp
private val AUTHOR_GAP = 8.dp
private val BOTTOM_BLOCK_PADDING = 16.dp
