package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [FeedDetailPlaceholder] — the
 * `surfaceContainerLow`-tinted "Select a post to read" empty state shown
 * in the right pane when `[Feed]` is the only entry on the back stack at
 * medium/expanded widths.
 *
 * Two baselines (light + dark) — the icon is decorative and the prompt
 * is sourced from `R.string.feed_detail_placeholder_select`.
 */

@PreviewTest
@Preview(name = "feed-detail-placeholder-light", showBackground = true)
@Composable
private fun FeedDetailPlaceholderLightScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedDetailPlaceholder()
    }
}

@PreviewTest
@Preview(
    name = "feed-detail-placeholder-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedDetailPlaceholderDarkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedDetailPlaceholder()
    }
}
