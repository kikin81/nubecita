package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import net.kikin.nubecita.core.feeds.ReplyVisibility
import net.kikin.nubecita.core.feeds.withReplyVisibility
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Baselines for the Feed preferences screen.
 *
 * Two fixtures, chosen so the pair actually discriminates: the shipped default
 * (Replies = People you follow, both switches off) and an all-filtered state
 * (Replies = None, both switches on). A regression that stuck the reply segment
 * or ignored the switch state would change one of these.
 */
@PreviewTest
@Preview(name = "feed-preferences-default-light", showBackground = true, heightDp = 640)
@Preview(
    name = "feed-preferences-default-dark",
    showBackground = true,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedPreferencesDefaultScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            FeedPreferencesContent(
                state = feedPreferencesPreviewState(),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "feed-preferences-all-filtered-light", showBackground = true, heightDp = 640)
@Preview(
    name = "feed-preferences-all-filtered-dark",
    showBackground = true,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedPreferencesAllFilteredScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            FeedPreferencesContent(
                state =
                    feedPreferencesPreviewState(
                        FeedViewPrefs
                            .DEFAULT
                            .withReplyVisibility(ReplyVisibility.NONE)
                            .copy(hideReposts = true, hideQuotePosts = true),
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
