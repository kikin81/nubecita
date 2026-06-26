package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for galleries (`app.bsky.embed.gallery`, 5–10
 * images). Galleries reuse [PostCardImageEmbed]'s multi-image carousel
 * (gallery is rendered identically to images, just past the 4-image
 * `images` cap), so these fixtures pin the carousel geometry at gallery
 * counts — verifying it renders without crashing or clipping at N = 5
 * and the soft cap of 10. Image cells render the placeholder painter
 * (preview tooling doesn't hit the network).
 */

@PreviewTest
@Preview(name = "gallery-five-light", showBackground = true)
@Preview(name = "gallery-five-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardGalleryEmbedFiveScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = galleryPreviewImages(5))
    }
}

@PreviewTest
@Preview(name = "gallery-ten-light", showBackground = true)
@Preview(name = "gallery-ten-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardGalleryEmbedTenScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = galleryPreviewImages(10))
    }
}

private fun galleryPreviewImages(count: Int) =
    (0 until count)
        .map { index ->
            ImageUi(
                fullsizeUrl = "https://example.com/placeholder/$index.jpg",
                thumbUrl = "https://example.com/placeholder/$index.jpg",
                altText = "Placeholder image $index",
                aspectRatio = 1.5f,
            )
        }.toImmutableList()
