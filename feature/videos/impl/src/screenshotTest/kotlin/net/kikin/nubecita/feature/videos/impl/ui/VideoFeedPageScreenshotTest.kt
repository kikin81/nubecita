package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

private const val CANVAS_HEIGHT_DP = 600

/**
 * Ratio-matched 1-colour posters embedded as `data:` URIs.
 *
 * layoutlib has no network, so a remote URL renders Coil's error painter — which
 * this surface deliberately paints BLACK (spec D4), making every baseline a
 * byte-identical black rectangle that discriminates nothing. An embedded image
 * resolves without I/O, so the letterbox geometry is actually visible and a
 * regression in it can actually fail.
 *
 * Each is sized to the ratio of the preview that uses it (9:16 / 16:9) so
 * `ContentScale.Fit` fills the ratio-locked box exactly; a square source would
 * inset inside those bounds and pin misleading geometry.
 */
private const val POSTER_9X16 =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAkAAAAQCAIAAABLKsIUAAAAF0lEQVR42mOsWPWfAQdgYsANRuUGhxwA0uQCQS3T4GYAAAAASUVORK5CYII="

private const val POSTER_16X9 =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAJCAIAAAC0SDtlAAAAF0lEQVR42mOsWPWfgRTAxEAiGNVAEw0At3cCM1TB5YoAAAAASUVORK5CYII="

/**
 * The feed's canvas is `Color.Black` at every theme — `VideoFeedScreen` sets
 * `Scaffold(containerColor = Color.Black)` for a full-bleed video surface. So
 * previews wrap in an explicit black Box rather than NubecitaCanvasPreviewTheme
 * (which paints the theme's `surface`, i.e. white in light mode and would pin
 * white letterbox bars that never ship).
 *
 * For the same reason there are no dark variants: this surface does not respond
 * to theme, so a dark baseline would be byte-identical to its light twin.
 */
@Composable
private fun VideoFeedCanvas(content: @Composable () -> Unit) {
    NubecitaTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { content() }
    }
}

/** Portrait clip, poster fully covering — the state every cold page opens in. */
@PreviewTest
@Preview(name = "poster-portrait", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun VideoFeedPagePortraitPreview() {
    VideoFeedCanvas {
        VideoFeedPage(posterUrl = POSTER_9X16, aspectRatio = 9f / 16f, posterAlpha = { 1f })
    }
}

/** Landscape clip — pins the letterbox bars the deferred blur fill will replace. */
@PreviewTest
@Preview(name = "poster-landscape", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun VideoFeedPageLandscapePreview() {
    VideoFeedCanvas {
        VideoFeedPage(posterUrl = POSTER_16X9, aspectRatio = 16f / 9f, posterAlpha = { 1f })
    }
}

/** Mid-crossfade, so a regression that breaks the alpha plumbing is visible. */
@PreviewTest
@Preview(name = "poster-midfade", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun VideoFeedPageMidFadePreview() {
    VideoFeedCanvas {
        VideoFeedPage(posterUrl = POSTER_9X16, aspectRatio = 9f / 16f, posterAlpha = { 0.5f })
    }
}

/** Missing poster — spec D4's flat-black degrade, invisible against the canvas. */
@PreviewTest
@Preview(name = "poster-missing", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun VideoFeedPageMissingPosterPreview() {
    VideoFeedCanvas {
        VideoFeedPage(posterUrl = null, aspectRatio = 9f / 16f, posterAlpha = { 1f })
    }
}
