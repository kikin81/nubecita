package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Renders 1–4 images from a Bluesky `app.bsky.embed.images` embed using a
 * deterministic geometry that matches the reference UI kit:
 *
 * - 1: full-width, height-capped at [EMBED_HEIGHT], `ContentScale.Crop`
 * - 2: equal-weight side-by-side columns
 * - 3: tall column on the left, two stacked half-height cells on the right
 * - 4: 2×2 grid
 *
 * Every cell consumes [NubecitaAsyncImage] for placeholder / error / crossfade
 * defaults and clips to a 16dp rounded corner. Lists with 0 items render
 * nothing (defensive — the mapper SHOULD never produce an empty Images embed,
 * but Box-of-nothing is safer than throwing).
 */
@Composable
fun PostCardImageEmbed(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
) {
    when (items.size) {
        0 -> Unit
        1 -> SingleImage(items[0], modifier)
        2 -> TwoImages(items[0], items[1], modifier)
        3 -> ThreeImages(items[0], items[1], items[2], modifier)
        else -> FourImages(items[0], items[1], items[2], items[3], modifier)
    }
}

@Composable
private fun SingleImage(
    image: ImageUi,
    modifier: Modifier = Modifier,
) {
    NubecitaAsyncImage(
        model = image.url,
        contentDescription = image.altText,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = EMBED_HEIGHT)
                .clip(IMAGE_SHAPE),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun TwoImages(
    left: ImageUi,
    right: ImageUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(EMBED_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(EMBED_GAP),
    ) {
        ImageCell(left, Modifier.weight(1f).fillMaxSize())
        ImageCell(right, Modifier.weight(1f).fillMaxSize())
    }
}

@Composable
private fun ThreeImages(
    primary: ImageUi,
    topRight: ImageUi,
    bottomRight: ImageUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(EMBED_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(EMBED_GAP),
    ) {
        ImageCell(primary, Modifier.weight(1f).fillMaxSize())
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(EMBED_GAP),
        ) {
            ImageCell(topRight, Modifier.weight(1f).fillMaxSize())
            ImageCell(bottomRight, Modifier.weight(1f).fillMaxSize())
        }
    }
}

@Composable
private fun FourImages(
    topLeft: ImageUi,
    topRight: ImageUi,
    bottomLeft: ImageUi,
    bottomRight: ImageUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().height(EMBED_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(EMBED_GAP),
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(EMBED_GAP),
        ) {
            ImageCell(topLeft, Modifier.weight(1f).fillMaxSize())
            ImageCell(topRight, Modifier.weight(1f).fillMaxSize())
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(EMBED_GAP),
        ) {
            ImageCell(bottomLeft, Modifier.weight(1f).fillMaxSize())
            ImageCell(bottomRight, Modifier.weight(1f).fillMaxSize())
        }
    }
}

@Composable
private fun ImageCell(
    image: ImageUi,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(IMAGE_SHAPE)) {
        NubecitaAsyncImage(
            model = image.url,
            contentDescription = image.altText,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private val EMBED_HEIGHT: Dp = 180.dp
private val EMBED_GAP: Dp = 4.dp
private val IMAGE_SHAPE = RoundedCornerShape(16.dp)

@Preview(name = "Image embed — single", showBackground = true)
@Composable
private fun PostCardImageEmbedSinglePreview() {
    NubecitaTheme {
        PostCardImageEmbed(items = persistentListOf(previewImage(0)))
    }
}

@Preview(name = "Image embed — two", showBackground = true)
@Composable
private fun PostCardImageEmbedTwoPreview() {
    NubecitaTheme {
        PostCardImageEmbed(items = persistentListOf(previewImage(0), previewImage(1)))
    }
}

@Preview(name = "Image embed — three", showBackground = true)
@Composable
private fun PostCardImageEmbedThreePreview() {
    NubecitaTheme {
        PostCardImageEmbed(
            items = persistentListOf(previewImage(0), previewImage(1), previewImage(2)),
        )
    }
}

@Preview(name = "Image embed — four", showBackground = true)
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
