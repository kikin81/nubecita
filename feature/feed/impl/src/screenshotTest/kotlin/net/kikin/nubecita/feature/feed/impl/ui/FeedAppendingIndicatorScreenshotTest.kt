package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaScreenPreviewTheme

/**
 * Screenshot baseline for [FeedAppendingIndicator]. Single matrix
 * (light + dark) — the indicator is a thin wrapper over PostCardShimmer
 * so this exists primarily as a regression guard if the indicator visual
 * is ever swapped (e.g., circular spinner instead of shimmer row).
 */
@PreviewTest
@Preview(name = "appending-light", showBackground = true)
@Preview(name = "appending-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedAppendingIndicatorScreenshot() {
    NubecitaScreenPreviewTheme {
        FeedAppendingIndicator()
    }
}
