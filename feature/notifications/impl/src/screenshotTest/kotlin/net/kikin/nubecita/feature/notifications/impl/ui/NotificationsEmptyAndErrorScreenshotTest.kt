package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.notifications.impl.NotificationsError

/**
 * Screenshot baselines for the full-screen empty / initial-error
 * layouts. Hosted under [NubecitaCanvasPreviewTheme] so the canvas
 * paint matches the production `Scaffold(containerColor = surface)`
 * rendering.
 */

@PreviewTest
@Preview(name = "empty-state-light", showBackground = true)
@Preview(name = "empty-state-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsEmptyStateScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsEmptyState()
    }
}

@PreviewTest
@Preview(name = "initial-error-network-light", showBackground = true)
@Preview(name = "initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsInitialErrorNetworkScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsInitialError(error = NotificationsError.Network, onRetry = {})
    }
}

@PreviewTest
@Preview(name = "initial-error-unauthenticated-light", showBackground = true)
@Composable
private fun NotificationsInitialErrorUnauthScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsInitialError(error = NotificationsError.Unauthenticated, onRetry = {})
    }
}

@PreviewTest
@Preview(name = "initial-error-unknown-light", showBackground = true)
@Composable
private fun NotificationsInitialErrorUnknownScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsInitialError(error = NotificationsError.Unknown, onRetry = {})
    }
}
