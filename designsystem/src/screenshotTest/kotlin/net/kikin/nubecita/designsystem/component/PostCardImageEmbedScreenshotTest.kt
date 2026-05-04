package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardImageEmbed]'s 1/2/3/4-image grid
 * geometries. Image cells render the placeholder painter (preview
 * tooling doesn't hit the network) so the baselines verify layout +
 * cell sizing + corner clipping, not the image content itself.
 */

@PreviewTest
@Preview(name = "single-light", showBackground = true)
@Preview(name = "single-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardImageEmbedSingleScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = persistentListOf(previewImage(0)))
    }
}

@PreviewTest
@Preview(name = "two-light", showBackground = true)
@Preview(name = "two-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardImageEmbedTwoScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = persistentListOf(previewImage(0), previewImage(1)))
    }
}

@PreviewTest
@Preview(name = "three-light", showBackground = true)
@Preview(name = "three-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardImageEmbedThreeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(
            items = persistentListOf(previewImage(0), previewImage(1), previewImage(2)),
        )
    }
}

@PreviewTest
@Preview(name = "four-light", showBackground = true)
@Preview(name = "four-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardImageEmbedFourScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(
            items =
                persistentListOf(
                    previewImage(0),
                    previewImage(1),
                    previewImage(2),
                    previewImage(3),
                ),
        )
    }
}

/**
 * 4:5 portrait — inside the `[2/3, 3/1]` clamp range, so the image
 * renders at its native aspect with no crop. Pins the IGN /
 * head-and-shoulders behavior fixed in nubecita-k9k: the photo no
 * longer letterbox-slits to a 180dp horizontal band.
 */
@PreviewTest
@Preview(name = "single-portrait-light", showBackground = true)
@Preview(name = "single-portrait-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardImageEmbedSinglePortraitScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 4f / 5f)))
    }
}

/**
 * 9:16 phone screenshot — below `MIN_ASPECT_RATIO`, so the displayed
 * canvas is clamped to 2:3 and `ContentScale.Crop` center-crops top
 * and bottom. Pins the upper-bound behavior of the clamp range.
 */
@PreviewTest
@Preview(name = "single-ultra-tall-light", showBackground = true)
@Preview(name = "single-ultra-tall-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardImageEmbedSingleUltraTallScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 9f / 16f)))
    }
}

private fun previewImage(
    index: Int,
    aspectRatio: Float? = 1.5f,
): ImageUi =
    ImageUi(
        url = "https://example.com/placeholder/$index.jpg",
        altText = "Placeholder image $index",
        aspectRatio = aspectRatio,
    )
