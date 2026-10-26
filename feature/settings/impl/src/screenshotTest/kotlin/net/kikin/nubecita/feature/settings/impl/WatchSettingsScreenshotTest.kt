package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private val FEEDS =
    persistentListOf(
        WatchFeedOption(uri = "following", kind = FeedKind.Following, displayName = "", avatarUrl = null),
        WatchFeedOption(uri = "at://discover", kind = FeedKind.Generator, displayName = "Discover", avatarUrl = null),
        WatchFeedOption(uri = "at://cats", kind = FeedKind.Generator, displayName = "Cats", avatarUrl = null),
        WatchFeedOption(uri = "at://art", kind = FeedKind.List, displayName = "Art I follow", avatarUrl = null),
    )

@Composable
private fun Watch(selected: Set<String>) {
    NubecitaCanvasPreviewTheme {
        Surface {
            WatchSettingsContent(
                state = WatchSettingsState(feeds = FEEDS, selectedUris = persistentSetOf(*selected.toTypedArray())),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

// Default: only Following, which is the last checked feed and so can't be unchecked.
@PreviewTest
@Preview(name = "watch-default-light", showBackground = true, heightDp = 700)
@Composable
private fun WatchDefaultScreenshot() = Watch(setOf("following"))

// At the cap: the unchecked feed is disabled; the checked ones stay toggleable.
@PreviewTest
@Preview(name = "watch-at-cap-light", showBackground = true, heightDp = 700)
@Composable
private fun WatchAtCapScreenshot() = Watch(setOf("following", "at://discover", "at://cats"))

@PreviewTest
@Preview(
    name = "watch-at-cap-dark",
    showBackground = true,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun WatchAtCapDarkScreenshot() = Watch(setOf("following", "at://discover", "at://cats"))
