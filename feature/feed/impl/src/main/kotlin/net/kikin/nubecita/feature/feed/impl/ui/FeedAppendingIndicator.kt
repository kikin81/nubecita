package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCardShimmer

/**
 * Tail-of-list loading indicator for the Following timeline's append path.
 *
 * Wraps [PostCardShimmer] (without an image placeholder so the row is
 * compact) so a future change can swap to a circular indicator or
 * different visual without touching `FeedScreen`. Hosted by `FeedScreen`
 * as a single trailing `LazyColumn` item when
 * `FeedState.loadStatus == FeedLoadStatus.Appending`.
 */
@Composable
internal fun FeedAppendingIndicator(modifier: Modifier = Modifier) {
    PostCardShimmer(modifier = modifier, showImagePlaceholder = false)
}

@Preview(name = "Appending — light", showBackground = true)
@Preview(name = "Appending — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedAppendingIndicatorPreview() {
    NubecitaTheme {
        FeedAppendingIndicator()
    }
}
