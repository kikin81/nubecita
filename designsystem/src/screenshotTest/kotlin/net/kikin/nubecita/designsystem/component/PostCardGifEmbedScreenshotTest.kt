package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardGifEmbed], in **light + dark**. The
 * animated frame is loaded by Coil at runtime and renders as the async-image
 * placeholder under layoutlib, so these pin the LAYOUT — full width, the 16dp
 * rounded clip, and the reserved height — for the known-aspect (exact box) and
 * unknown-aspect (height-band) cases, not the GIF content. `dynamicColor =
 * false` keeps the placeholder color deterministic across run environments.
 */
@PreviewTest
@Preview(name = "GIF — known aspect, light", showBackground = true)
@Preview(name = "GIF — known aspect, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardGifEmbedKnownAspectPreview() {
    NubecitaTheme(dynamicColor = false) {
        PostCardGifEmbed(gifUrl = "https://static.klipy.com/example.gif", aspectRatio = 0.93f, alt = "example gif")
    }
}

@PreviewTest
@Preview(name = "GIF — unknown aspect, light", showBackground = true)
@Preview(name = "GIF — unknown aspect, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardGifEmbedUnknownAspectPreview() {
    NubecitaTheme(dynamicColor = false) {
        PostCardGifEmbed(gifUrl = "https://media.tenor.com/example.gif", aspectRatio = null, alt = null)
    }
}
