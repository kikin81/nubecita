package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.feed.impl.FeedError

/**
 * Screenshot baselines for every [FeedErrorState] variant — Network,
 * Unauthenticated, Unknown, FeedOffline (with and without an upstream
 * message) and FeedNotFound — each in light + dark.
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

// The two FeedOffline baselines differ only by the presence of the
// upstream-message line — that is the point of having both. If they ever
// render byte-identical, the conditional line has stopped rendering.
@PreviewTest
@Preview(name = "feed-offline-light", showBackground = true)
@Preview(name = "feed-offline-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateFeedOfflineScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedErrorState(error = FeedError.FeedOffline(serverMessage = "feed unavailable"), onRetry = {})
    }
}

@PreviewTest
@Preview(name = "feed-offline-no-message-light", showBackground = true)
@Preview(
    name = "feed-offline-no-message-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedErrorStateFeedOfflineNoMessageScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedErrorState(error = FeedError.FeedOffline(serverMessage = null), onRetry = {})
    }
}

@PreviewTest
@Preview(name = "feed-not-found-light", showBackground = true)
@Preview(name = "feed-not-found-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateFeedNotFoundScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedErrorState(error = FeedError.FeedNotFound, onRetry = {})
    }
}
