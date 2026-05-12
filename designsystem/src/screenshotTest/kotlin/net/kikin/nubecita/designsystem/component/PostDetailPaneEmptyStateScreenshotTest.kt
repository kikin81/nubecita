package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostDetailPaneEmptyState]. Two themes,
 * widthDp = 400 (matches the detail-pane proportion at Medium screen
 * widths; the composable adapts to whatever pane size the strategy
 * gives it).
 */
@PreviewTest
@Preview(name = "placeholder-light", showBackground = true, widthDp = 400, heightDp = 600)
@Preview(
    name = "placeholder-dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostDetailPaneEmptyStateScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailPaneEmptyState()
    }
}
