package net.kikin.nubecita.wear.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.gate.GateMessageScreen
import net.kikin.nubecita.wear.ui.WearScrollIndicator

/**
 * The selected synced feed. Touch fling and the crown both snap item by item.
 * Shows the stored feed whether or not the phone is in reach, with when it
 * was last updated; "Switch feed" appears when more than one feed is synced.
 */
@Composable
fun FeedScreen(
    state: WatchFeedState,
    nowMs: Long,
    onPostClick: (WatchPostUi) -> Unit,
    onSwitchFeed: () -> Unit,
    modifier: Modifier = Modifier,
    unreadMessages: Int = 0,
    onOpenMessages: (() -> Unit)? = null,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    when (state.status) {
        // Brief: the local store answers fast. Plain black, no spinner.
        WatchFeedStatus.Loading -> return
        WatchFeedStatus.NoFeeds -> {
            GateMessageScreen(
                title = stringResource(R.string.wear_feed_empty_title),
                body = stringResource(R.string.wear_feed_empty_body),
                modifier = modifier,
            )
            return
        }
        WatchFeedStatus.Ready -> Unit
    }
    val feed = state.selectedFeed ?: return
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        scrollIndicator = { WearScrollIndicator(listState) },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
            rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(listState),
        ) {
            // One compact header so the first post is readable at a glance.
            item {
                FeedHeader(
                    feedName = feed.label(),
                    onSwitchFeed = if (state.feeds.size > 1) onSwitchFeed else null,
                    unreadMessages = unreadMessages,
                    onOpenMessages = onOpenMessages,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            items(state.posts, key = { it.uri }) { post ->
                Card(
                    onClick = { onPostClick(post) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        post.repostedBy?.let { by ->
                            Text(
                                stringResource(R.string.wear_post_reposted_by, by),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AuthorRow(post, nowMs)
                        if (post.text.isNotBlank()) {
                            Text(post.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        PostMedia(post, imageHeight = 96)
                        PostCounts(post)
                    }
                }
            }
        }
    }
}

/**
 * The feed's header in one row: the feed's name as its own switcher (a plain title when there's nothing to switch to) and,
 * once the phone has synced DMs, Messages beside it. Messages turns primary
 * only while something is unread, so it doesn't compete with the timeline.
 */
@Composable
private fun FeedHeader(
    feedName: String,
    onSwitchFeed: (() -> Unit)?,
    unreadMessages: Int,
    onOpenMessages: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (onSwitchFeed == null && onOpenMessages == null) {
            Text(feedName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@Column
        }
        val switchLabel = stringResource(R.string.wear_feed_switch)
        // Compact buttons sized to their content: the top of a round screen is
        // narrow, and full-size buttons truncated the feed name.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (onSwitchFeed != null) {
                CompactButton(
                    onClick = onSwitchFeed,
                    modifier =
                        Modifier.weight(1f, fill = false).semantics {
                            onClick(label = switchLabel) {
                                onSwitchFeed()
                                true
                            }
                        },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = {
                        Text(feedName, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(painter = painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            } else {
                Text(feedName, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onOpenMessages != null) {
                val description =
                    if (unreadMessages > 0) {
                        stringResource(R.string.wear_dms_open_unread, unreadMessages)
                    } else {
                        stringResource(R.string.wear_dms_open)
                    }
                CompactButton(
                    onClick = onOpenMessages,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                    // Primary only while something is unread.
                    colors = if (unreadMessages > 0) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painter = painterResource(R.drawable.ic_messages), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = if (unreadMessages > 0) ({ Text(unreadMessages.toString()) }) else null,
                )
            }
        }
    }
}
