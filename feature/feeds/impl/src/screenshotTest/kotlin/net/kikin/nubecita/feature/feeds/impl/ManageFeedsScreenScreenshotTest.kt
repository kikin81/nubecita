package net.kikin.nubecita.feature.feeds.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for [ManageFeedsContent] — the stateless body of the
 * manage-feeds screen (no ViewModel, so it renders without Hilt).
 *
 * Fixtures use `avatarUrl = null` so the deterministic glyph fallbacks render
 * (Following → Home, generator/list → globe) rather than a Coil/network image.
 * The Following row shows no remove affordance; the other rows do.
 */
private val FIXTURE_FEEDS =
    persistentListOf(
        PinnedFeedUi("following", "following", FeedKind.Following, "Following", null),
        PinnedFeedUi("at://art", "at://art", FeedKind.Generator, "Art & Design", null),
        PinnedFeedUi("at://science", "at://science", FeedKind.Generator, "Science", null),
        PinnedFeedUi("at://friends", "at://friends", FeedKind.List, "Close Friends", null),
    )

@PreviewTest
@Preview(name = "manage-feeds-populated-light", showBackground = true, heightDp = 720)
@Preview(
    name = "manage-feeds-populated-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ManageFeedsPopulatedScreenshot() {
    NubecitaCanvasPreviewTheme {
        ManageFeedsContent(
            status = ManageFeedsLoadStatus.Content(FIXTURE_FEEDS),
            onEvent = {},
            onBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@PreviewTest
@Preview(name = "manage-feeds-loading-light", showBackground = true, heightDp = 720)
@Preview(
    name = "manage-feeds-loading-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ManageFeedsLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        ManageFeedsContent(
            status = ManageFeedsLoadStatus.Loading,
            onEvent = {},
            onBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
