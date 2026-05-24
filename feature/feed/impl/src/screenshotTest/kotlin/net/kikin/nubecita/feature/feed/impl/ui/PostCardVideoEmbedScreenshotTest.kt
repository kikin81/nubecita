package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardVideoEmbed]'s phase-B variants.
 *
 * Coverage matrix:
 * - **with poster** — exercises the `NubecitaAsyncImage` branch (renders
 *   the Coil placeholder painter since the screenshot harness doesn't
 *   hit the network — the baseline verifies layout + corner clipping,
 *   not the poster pixels themselves).
 * - **without poster** — exercises the `GradientPosterFallback` branch.
 * - **short / long duration chip** — exercises the `m:ss` and `h:mm:ss`
 *   format paths with synthetic non-null `durationSeconds`. Even though
 *   v1 mapper passes `null` (the lexicon doesn't currently expose
 *   duration), the chip's render path is in place for a future phase
 *   and these baselines guard the layout so a regression would surface
 *   when duration is wired in.
 *
 * Each variant runs in light + dark mode for the dynamic-color-disabled
 * `NubecitaTheme` (matches the rest of the screenshot suite).
 */

@PreviewTest
@Preview(name = "with-poster-light", showBackground = true)
@Preview(name = "with-poster-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardVideoEmbedWithPosterScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardVideoEmbed(video = previewVideo())
    }
}

@PreviewTest
@Preview(name = "no-poster-light", showBackground = true)
@Preview(name = "no-poster-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardVideoEmbedNoPosterScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardVideoEmbed(video = previewVideo(posterUrl = null))
    }
}

@PreviewTest
@Preview(name = "short-duration-light", showBackground = true)
@Preview(name = "short-duration-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardVideoEmbedShortDurationScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardVideoEmbed(video = previewVideo(durationSeconds = 32))
    }
}

@PreviewTest
@Preview(name = "long-duration-light", showBackground = true)
@Preview(name = "long-duration-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardVideoEmbedLongDurationScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardVideoEmbed(video = previewVideo(durationSeconds = 3600 + 23 * 60 + 45))
    }
}
