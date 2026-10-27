package net.kikin.nubecita.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.feed.api.Feed

private const val SPATIAL_PANEL_WIDTH_DP: Int = 1024
private const val SPATIAL_PANEL_HEIGHT_DP: Int = 768

@PreviewTest
@Preview(
    name = "main-shell-spatial-panel-feed-selected",
    widthDp = SPATIAL_PANEL_WIDTH_DP,
    heightDp = SPATIAL_PANEL_HEIGHT_DP,
)
@Composable
private fun MainShellSpatialPanelFeedSelectedPreview() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Spatial Panel Full-Bleed Content",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
