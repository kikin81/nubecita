package net.kikin.nubecita.wear.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.ui.WearScrollIndicator

/**
 * One post in full: the whole text and a larger image. The author is a centered
 * header because the top of a round screen is too narrow for a left-aligned
 * row; the repost line and body come after it, where the screen is wider. Plain text items take
 * no `transformedHeight` (that edge morph is for surfaces and crops text).
 */
@Composable
fun PostScreen(
    post: WatchPostUi,
    nowMs: Long,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        scrollIndicator = { WearScrollIndicator(listState) },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { CenteredAuthor(post, nowMs) }
            post.repostedBy?.let { by ->
                item {
                    Text(
                        stringResource(R.string.wear_post_reposted_by, by),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (post.text.isNotBlank()) {
                item { Text(post.text, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium) }
            }
            item { PostMedia(post, imageHeight = 140) }
            item { PostCounts(post) }
        }
    }
}

/** Avatar, name and time stacked and centered: fits the narrow top of a round screen. */
@Composable
private fun CenteredAuthor(
    post: WatchPostUi,
    nowMs: Long,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AuthorAvatar(post.avatarPath)
        Text(post.authorName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            relativeTime(post.createdAtMs, nowMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
