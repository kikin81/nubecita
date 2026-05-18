package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [VideoPosterEmbed]:
 *
 * - `with-poster` exercises the `NubecitaAsyncImage` branch. The Coil
 *   load never resolves under preview tooling (no network), so the
 *   baseline renders the async-image placeholder under the play badge —
 *   which is fine for the layout / play-badge composition check.
 * - `no-poster` exercises the gradient-fallback branch (the path the
 *   feed's `PostCardVideoEmbed` also takes when the lexicon's `thumbnail`
 *   field is absent).
 * - Portrait aspect-ratio variant guards the `Modifier.aspectRatio(...)`
 *   gate against the 9:16 short-video shape that's common in Bluesky.
 *
 * Light + dark variants per the project's screenshot-test convention.
 */

@PreviewTest
@Preview(name = "with-poster-light", showBackground = true)
@Preview(name = "with-poster-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPosterEmbedWithPosterScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPosterEmbed(
            posterUrl = "https://example.com/poster.jpg",
            aspectRatio = 16f / 9f,
            altText = "Short clip of a cat playing piano",
            onTap = {},
        )
    }
}

@PreviewTest
@Preview(name = "no-poster-light", showBackground = true)
@Preview(name = "no-poster-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPosterEmbedNoPosterScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPosterEmbed(
            posterUrl = null,
            aspectRatio = 16f / 9f,
            altText = null,
            onTap = {},
        )
    }
}

@PreviewTest
@Preview(name = "portrait-light", showBackground = true)
@Preview(name = "portrait-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPosterEmbedPortraitScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPosterEmbed(
            posterUrl = null,
            aspectRatio = 9f / 16f,
            altText = null,
            onTap = {},
        )
    }
}

// Long-altText baseline: altText is contentDescription-only today, so
// the rendered surface looks identical to the with-poster baseline.
// The screenshot exists as a regression lock — if a future change
// starts displaying altText visually (caption strip, overlay label,
// etc.) the layout shift will surface here before it ships. Per
// nubecita-zak.7 acceptance: "with very long altText (a11y truncation
// check)".
@PreviewTest
@Preview(name = "long-alttext-light", showBackground = true)
@Preview(name = "long-alttext-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPosterEmbedLongAltTextScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPosterEmbed(
            posterUrl = "https://example.com/poster.jpg",
            aspectRatio = 16f / 9f,
            altText =
                "An exceptionally long video alt-text describing the scene in detail " +
                    "for screen-reader users — covers subjects, setting, on-screen text, " +
                    "and notable audio cues. Long enough to surface any future truncation " +
                    "or overflow regressions if the surface starts rendering altText " +
                    "as visible chrome.",
            onTap = {},
        )
    }
}
