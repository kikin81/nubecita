package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Baselines for the seam's two affordances, light and dark.
 *
 * The point of the row is that the break in the timeline is never bare, so
 * what these pin is that each state actually says something and offers a way
 * out — the failure these catch is a seam that renders as two dividers with
 * nothing between them.
 *
 * The in-flight state is deliberately absent: its loading indicator animates,
 * and a frame of an animation is not something a committed baseline should
 * depend on. The UI test covers it instead.
 */
@PreviewTest
@Preview(name = "seam-fillable-light", showBackground = true)
@Preview(name = "seam-fillable-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedSeamRowFillableScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedSeamRow(exhausted = false, isFilling = false, onFill = {}, onJumpToNewest = {})
    }
}

@PreviewTest
@Preview(name = "seam-exhausted-light", showBackground = true)
@Preview(name = "seam-exhausted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedSeamRowExhaustedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedSeamRow(exhausted = true, isFilling = false, onFill = {}, onJumpToNewest = {})
    }
}
