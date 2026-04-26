package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [FeedEmptyState]. Light + dark in a single
 * `@PreviewTest` function via stacked `@Preview` annotations. Regenerate
 * with `./gradlew :feature:feed:impl:updateDebugScreenshotTest` after
 * intentional visual changes.
 */
@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedEmptyStateScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedEmptyState()
    }
}
