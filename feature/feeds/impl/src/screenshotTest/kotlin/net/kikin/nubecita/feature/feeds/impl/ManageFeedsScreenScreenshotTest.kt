package net.kikin.nubecita.feature.feeds.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baseline for the scaffolded [ManageFeedsScreen] — the titled
 * TopAppBar + back affordance on the screen canvas. Light/Dark = 2 baselines.
 *
 * Seeds the screenshot coverage for this surface (nubecita-ydfn.1). The
 * populated pinned-feeds list, a mid-drag lifted row, and the
 * Following-without-remove fixtures are added alongside the real UI in
 * nubecita-ydfn.5.
 */
@PreviewTest
@Preview(name = "manage-feeds-scaffold-light", showBackground = true, heightDp = 720)
@Preview(
    name = "manage-feeds-scaffold-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ManageFeedsScaffoldScreenshot() {
    NubecitaCanvasPreviewTheme {
        ManageFeedsScreen(onBack = {})
    }
}
