package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.feed.impl.FeedError

/**
 * Screenshot baselines for [FeedErrorState]'s three error variants
 * (Network, Unauthenticated, Unknown), each in light + dark.
 */

@PreviewTest
@Preview(name = "network-light", showBackground = true)
@Preview(name = "network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateNetworkScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedErrorState(error = FeedError.Network, onRetry = {})
    }
}

@PreviewTest
@Preview(name = "unauthenticated-light", showBackground = true)
@Preview(name = "unauthenticated-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateUnauthenticatedScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedErrorState(error = FeedError.Unauthenticated, onRetry = {})
    }
}

@PreviewTest
@Preview(name = "unknown-light", showBackground = true)
@Preview(name = "unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateUnknownScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedErrorState(error = FeedError.Unknown(cause = null), onRetry = {})
    }
}
