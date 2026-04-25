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

private fun previewImage(index: Int): ImageUi =
    ImageUi(
        url = "https://example.com/placeholder/$index.jpg",
        altText = "Placeholder image $index",
        aspectRatio = 1.5f,
    )
