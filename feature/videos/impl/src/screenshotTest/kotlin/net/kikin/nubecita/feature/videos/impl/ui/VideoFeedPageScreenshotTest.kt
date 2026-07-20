package net.kikin.nubecita.feature.videos.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private const val CANVAS_HEIGHT_DP = 600

/** Portrait clip, poster fully covering — the state every cold page opens in. */
@PreviewTest
@Preview(name = "poster-portrait-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(
    name = "poster-portrait-dark",
    showBackground = true,
    heightDp = CANVAS_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VideoFeedPagePortraitPreview() {
    NubecitaCanvasPreviewTheme {
        VideoFeedPage(posterUrl = null, aspectRatio = 9f / 16f, posterAlpha = 1f)
    }
}

/** Landscape clip — pins the letterbox bars the deferred blur fill will replace. */
@PreviewTest
@Preview(name = "poster-landscape-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(
    name = "poster-landscape-dark",
    showBackground = true,
    heightDp = CANVAS_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VideoFeedPageLandscapePreview() {
    NubecitaCanvasPreviewTheme {
        VideoFeedPage(posterUrl = null, aspectRatio = 16f / 9f, posterAlpha = 1f)
    }
}

/** Mid-crossfade, so a regression that breaks the alpha plumbing is visible. */
@PreviewTest
@Preview(name = "poster-midfade-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(
    name = "poster-midfade-dark",
    showBackground = true,
    heightDp = CANVAS_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VideoFeedPageMidFadePreview() {
    NubecitaCanvasPreviewTheme {
        VideoFeedPage(posterUrl = null, aspectRatio = 9f / 16f, posterAlpha = 0.5f)
    }
}
