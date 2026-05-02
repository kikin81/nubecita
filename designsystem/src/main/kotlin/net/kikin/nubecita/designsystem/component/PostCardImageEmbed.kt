package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Renders a Bluesky `app.bsky.embed.images` embed.
 *
 * - **Single image (`items.size == 1`):** full-width, height-capped at
 *   [EMBED_HEIGHT], `ContentScale.Crop`, 16dp rounded corners. This path
 *   is the highest-traffic surface in the feed and is the regression
 *   contract for the m28.5.2 carousel introduction — its visual output
 *   MUST stay byte-for-byte unchanged when the multi-image branch is
 *   added.
 * - **Multi-image (`items.size > 1`):** delegates to M3's
 *   [HorizontalMultiBrowseCarousel]. Each slide is a single image clipped
 *   to the same 16dp shape; the carousel manages keyline-based sizing
 *   (large + medium + small slides simultaneously visible) and snap
 *   scrolling. No custom shape morphing — the M3 default item shape
 *   delta does the visual work.
 *
 * The lexicon allows 0–4 images per `app.bsky.embed.images` payload;
 * mappers SHOULD never produce an empty list, but the empty branch is
 * defensive (renders nothing rather than throwing).
 *
 * Image taps fire [onImageClick] with the per-image index. Default is
 * a no-op so existing call sites that don't care about taps (the feed,
 * which has no media-viewer wiring as of m28.5.2) compile unchanged.
 */
@Composable
fun PostCardImageEmbed(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
    onImageClick: (imageIndex: Int) -> Unit = {},
) {
    when (items.size) {
        0 -> Unit
        1 -> SingleImage(items[0], modifier, onClick = { onImageClick(0) })
        else -> MultiImageCarousel(items, modifier, onImageClick)
    }
}

@Composable
private fun SingleImage(
    image: ImageUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    NubecitaAsyncImage(
        model = image.url,
        contentDescription = image.altText,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = EMBED_HEIGHT)
                .clip(IMAGE_SHAPE)
                .clickable(onClick = onClick),
        contentScale = ContentScale.Crop,
    )
}

/**
 * M3 [HorizontalMultiBrowseCarousel] over the multi-image embed.
 *
 * Sizing notes:
 * - The carousel container fixes its height to [EMBED_HEIGHT] so a
 *   multi-image post takes the same vertical real estate as a
 *   single-image one (avoids unexpected layout jumps when a feed mixes
 *   1-image and N-image posts).
 * - [CAROUSEL_PREFERRED_ITEM_WIDTH] is M3's typical "medium" preferred
 *   width — the carousel's keyline math derives the small-slide widths
 *   from the defaults ([CarouselDefaults.MinSmallItemSize] /
 *   [CarouselDefaults.MaxSmallItemSize]). Per the m28.5.2 design intent
 *   this stays at the M3 default rather than being tuned to clone the
 *   single-image path's `fillMaxWidth()` sizing — the surfaces are not
 *   equivalent and forcing a match introduces letterboxing.
 * - [EMBED_GAP] is the inter-slide spacing, matching the existing
 *   inter-image gap from the legacy 2/3/4-cell grid.
 */
@Composable
private fun MultiImageCarousel(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
    onImageClick: (imageIndex: Int) -> Unit = {},
) {
    val state = rememberCarouselState(itemCount = { items.size })
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = CAROUSEL_PREFERRED_ITEM_WIDTH,
        modifier =
            modifier
                .fillMaxWidth()
                .height(EMBED_HEIGHT),
        itemSpacing = EMBED_GAP,
    ) { index ->
        val image = items[index]
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(IMAGE_SHAPE)
                    .clickable { onImageClick(index) },
        ) {
            NubecitaAsyncImage(
                model = image.url,
                contentDescription = image.altText,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private val EMBED_HEIGHT: Dp = 180.dp
private val EMBED_GAP: Dp = 4.dp
private val CAROUSEL_PREFERRED_ITEM_WIDTH: Dp = 220.dp
private val IMAGE_SHAPE = RoundedCornerShape(16.dp)

@Preview(name = "Image embed — single", showBackground = true)
@Composable
private fun PostCardImageEmbedSinglePreview() {
    NubecitaTheme {
        PostCardImageEmbed(items = persistentListOf(previewImage(0)))
    }
}

@Preview(name = "Image embed — two (carousel)", showBackground = true)
@Composable
private fun PostCardImageEmbedTwoPreview() {
    NubecitaTheme {
        PostCardImageEmbed(items = persistentListOf(previewImage(0), previewImage(1)))
    }
}

@Preview(name = "Image embed — three (carousel)", showBackground = true)
@Composable
private fun PostCardImageEmbedThreePreview() {
    NubecitaTheme {
        PostCardImageEmbed(
            items = persistentListOf(previewImage(0), previewImage(1), previewImage(2)),
        )
    }
}

@Preview(name = "Image embed — four (carousel)", showBackground = true)
@Composable
private fun PostCardImageEmbedFourPreview() {
    NubecitaTheme {
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
