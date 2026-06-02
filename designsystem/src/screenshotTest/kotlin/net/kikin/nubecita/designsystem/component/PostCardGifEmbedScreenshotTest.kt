package net.kikin.nubecita.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baseline for [PostCardGifEmbed]. The animated frame is loaded by
 * Coil at runtime and renders as the async-image placeholder under layoutlib,
 * so these baselines pin the layout — the aspect-ratio box, full width, and the
 * 16dp rounded clip — for the known-aspect and unknown-aspect (height-capped)
 * cases, in light + dark.
 */
@PreviewTest
@Preview(name = "GIF embed — known aspect", showBackground = true)
@Composable
private fun PostCardGifEmbedKnownAspectPreview() {
    NubecitaTheme {
        PostCardGifEmbed(gifUrl = "https://static.klipy.com/example.gif", aspectRatio = 0.93f, alt = "example gif")
    }
}

@PreviewTest
@Preview(name = "GIF embed — unknown aspect", showBackground = true)
@Composable
private fun PostCardGifEmbedUnknownAspectPreview() {
    NubecitaTheme {
        PostCardGifEmbed(gifUrl = "https://media.tenor.com/example.gif", aspectRatio = null, alt = null)
    }
}
