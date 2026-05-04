package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * - **Single image (`items.size == 1`):** full-width with the layout
 *   height derived from the post's `aspectRatio` (width/height), clamped
 *   to `[MIN_ASPECT_RATIO, MAX_ASPECT_RATIO]` = `[2/3, 3/1]`. Source
 *   aspects inside the range render natively with no crop. Source aspects
 *   outside the range are displayed at the clamp boundary and
 *   center-cropped via `ContentScale.Crop` (extreme verticals like
 *   webcomics get capped at 2:3; ultra-wide panoramas get capped at 3:1).
 *   When `aspectRatio` is null (older posts pre-2024), falls back to a
 *   square 1:1 canvas. This range tracks Bluesky's own client and X's
 *   feed treatment — pre-fullscreen mode is a digestible card; the
 *   fullscreen image viewer (separate epic) shows native aspect.
 * - **Multi-image (`items.size > 1`):** delegates to M3's
 *   [HorizontalMultiBrowseCarousel]. Each slide masks to the same 16dp
 *   base shape via [CarouselItemScope.maskClip], which lets the carousel
 *   morph the corner radius across the keyline math (slides shrink and
 *   pill-shape as they leave the center keyline). Using a plain
 *   [Modifier.clip] here would freeze the shape and defeat the keyline
 *   morphing — that is the bug fixed in nubecita-eaw.
 *
 * The lexicon allows 0–4 images per `app.bsky.embed.images` payload;
 * mappers SHOULD never produce an empty list, but the empty branch is
 * defensive (renders nothing rather than throwing).
 *
 * [onImageClick] is **opt-in** by being nullable with a default of `null`:
 * - `null` (default) — image cells render WITHOUT a clickable wrapper,
 *   so taps bubble up to the parent `PostCard`'s body-tap clickable.
 *   This is the contract the feed relies on (tapping any region of a
 *   PostCard, including the embed, opens post-detail).
 * - non-null — the per-image cell intercepts the tap and dispatches
 *   `onImageClick(index)`. Used by the post-detail Focus PostCard to
 *   open the fullscreen media viewer; the parent body-tap still fires
 *   for non-image regions.
 */
@Composable
fun PostCardImageEmbed(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
) {
    when (items.size) {
        0 -> Unit
        1 -> SingleImage(items[0], modifier, onClick = onImageClick?.let { handler -> { handler(0) } })
        else -> MultiImageCarousel(items, modifier, onImageClick)
    }
}

@Composable
private fun SingleImage(
    image: ImageUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier =
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    NubecitaAsyncImage(
        model = image.url,
        contentDescription = image.altText,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(image.displayedAspectRatio())
                .clip(IMAGE_SHAPE)
                .then(clickModifier),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Layout aspect (width/height) used by [SingleImage] for the post's
 * canvas, derived by clamping the source `aspectRatio` to `[MIN_ASPECT_RATIO,
 * MAX_ASPECT_RATIO]`. Null source aspects fall back to [FALLBACK_ASPECT_RATIO].
 *
 * Public-internal so the clamp math is unit-testable as a pure function
 * without spinning up a Compose harness.
 */
internal fun ImageUi.displayedAspectRatio(): Float = (aspectRatio ?: FALLBACK_ASPECT_RATIO).coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)

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
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
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
        // Only attach a per-slide clickable when the caller actually
        // wired one; otherwise the parent PostCard's body-tap claims
        // the gesture, matching the feed's "tap anywhere → post detail"
        // contract.
        val clickModifier =
            if (onImageClick != null) Modifier.clickable { onImageClick(index) } else Modifier
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .maskClip(IMAGE_SHAPE)
                    .then(clickModifier),
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

/**
 * Tallest portrait we'll display for a single image (2:3 = ~0.667 width/height).
 * Source aspects below this clamp here and center-crop top/bottom via
 * [ContentScale.Crop]. Tracks Bluesky / X feed treatment so a phone-screenshot
 * post doesn't take three screen heights.
 */
internal const val MIN_ASPECT_RATIO: Float = 2f / 3f

/**
 * Widest landscape we'll display for a single image (3:1 = 3.0 width/height).
 * Ultra-wide panoramas above this clamp here and center-crop left/right.
 */
internal const val MAX_ASPECT_RATIO: Float = 3f / 1f

/**
 * Default canvas aspect when the post's `aspectRatio` is null (older
 * Bluesky posts pre-2024 didn't carry the field). Square 1:1 is inside
 * the clamp range, so the fallback never triggers a crop.
 */
internal const val FALLBACK_ASPECT_RATIO: Float = 1f

@Preview(name = "Image embed — single landscape (3:2)", showBackground = true)
@Composable
private fun PostCardImageEmbedSingleLandscapePreview() {
    NubecitaTheme {
        // 3:2 landscape — well inside [2/3, 3/1], renders natively.
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 1.5f)))
    }
}

@Preview(name = "Image embed — single portrait (4:5)", showBackground = true)
@Composable
private fun PostCardImageEmbedSinglePortraitPreview() {
    NubecitaTheme {
        // 4:5 portrait — inside the range, renders natively (the IGN /
        // head-and-shoulders case from nubecita-k9k that no longer
        // letterbox-slits to 180dp).
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 0.8f)))
    }
}

@Preview(name = "Image embed — single ultra-tall (9:16, clamps)", showBackground = true)
@Composable
private fun PostCardImageEmbedSingleUltraTallPreview() {
    NubecitaTheme {
        // 9:16 phone screenshot — below MIN_ASPECT_RATIO, clamps to 2:3
        // and center-crops top/bottom via ContentScale.Crop.
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 9f / 16f)))
    }
}

@Preview(name = "Image embed — single null aspect (fallback square)", showBackground = true)
@Composable
private fun PostCardImageEmbedSingleNullAspectPreview() {
    NubecitaTheme {
        // Pre-2024 post with no aspectRatio in the lexicon — falls back
        // to FALLBACK_ASPECT_RATIO = 1.0 (square), which is inside the
        // clamp range so no crop triggers.
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = null)))
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

private fun previewImage(
    index: Int,
    aspectRatio: Float? = 1.5f,
): ImageUi =
    ImageUi(
        url = "https://example.com/placeholder/$index.jpg",
        altText = "Placeholder image $index",
        aspectRatio = aspectRatio,
    )
