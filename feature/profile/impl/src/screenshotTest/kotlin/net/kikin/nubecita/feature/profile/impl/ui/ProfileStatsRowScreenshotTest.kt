package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [ProfileStatsRow]. Covers the typical
 * three-digit case and the large-count case (1.4M followers) so the
 * locale-aware compact formatting and `·` separator are pinned. Regenerate
 * with `./gradlew :feature:profile:impl:updateDebugScreenshotTest` after
 * intentional visual changes.
 */
@PreviewTest
@Preview(name = "stats-typical-light", showBackground = true)
@Preview(name = "stats-typical-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileStatsRowTypicalScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileStatsRow(postsCount = 412, followersCount = 2_142, followsCount = 342)
    }
}

@PreviewTest
@Preview(name = "stats-large-light", showBackground = true)
@Preview(name = "stats-large-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileStatsRowLargeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileStatsRow(postsCount = 1_412, followersCount = 1_400_000, followsCount = 12_345)
    }
}
