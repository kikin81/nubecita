package net.kikin.nubecita.wear.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
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
            item {
                ListHeader(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(feed.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Text(
                    text = stringResource(R.string.wear_feed_updated, relativeTime(feed.updatedAtMs, nowMs)),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.feeds.size > 1) {
                item {
                    CompactButton(
                        onClick = onSwitchFeed,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = { Text(stringResource(R.string.wear_feed_switch)) },
                    )
                }
            }
            // Shown once the phone has synced DMs (W4).
            if (onOpenMessages != null) {
                item {
                    CompactButton(
                        onClick = onOpenMessages,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = {
                            Text(
                                if (unreadMessages > 0) {
                                    stringResource(R.string.wear_dms_open_unread, unreadMessages)
                                } else {
                                    stringResource(R.string.wear_dms_open)
                                },
                            )
                        },
                    )
                }
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
