package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

private const val CANVAS_HEIGHT_DP = 120

/**
 * The feed canvas is always black (VideoFeedScreen sets a black Scaffold), so the
 * fixture wraps in an explicit black Box rather than a themed surface — a themed
 * canvas would pin white behind the white bar. No dark variants: the bar does not
 * respond to theme, so a dark baseline is byte-identical to its light twin. The
 * three fractions produce three visibly distinct fill widths, so the baselines
 * discriminate.
 */
@Composable
private fun ProgressCanvas(progress: Float) {
    NubecitaTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.BottomCenter) {
            VideoProgressBarContent(progress = { progress })
        }
    }
}

@PreviewTest
@Preview(name = "progress-empty", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun ProgressEmptyPreview() = ProgressCanvas(progress = 0f)

@PreviewTest
@Preview(name = "progress-partial", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun ProgressPartialPreview() = ProgressCanvas(progress = 0.4f)

@PreviewTest
@Preview(name = "progress-full", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Composable
private fun ProgressFullPreview() = ProgressCanvas(progress = 1f)
