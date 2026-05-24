package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardUnsupportedEmbed]'s friendly-name
 * mapping. Adding a new lexicon URI to the mapping (or changing the
 * fallback "embed" label) will require regenerating these baselines.
 */

@PreviewTest
@Preview(name = "video-light", showBackground = true)
@Preview(name = "video-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardUnsupportedEmbedVideoScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardUnsupportedEmbed(typeUri = "app.bsky.embed.video")
    }
}

@PreviewTest
@Preview(name = "quoted-post-light", showBackground = true)
@Preview(name = "quoted-post-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardUnsupportedEmbedQuotedPostScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardUnsupportedEmbed(typeUri = "app.bsky.embed.record")
    }
}

@PreviewTest
@Preview(name = "unknown-light", showBackground = true)
@Preview(name = "unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardUnsupportedEmbedUnknownScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardUnsupportedEmbed(typeUri = "app.bsky.embed.somethingNew")
    }
}
