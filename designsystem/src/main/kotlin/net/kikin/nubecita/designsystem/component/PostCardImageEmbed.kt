package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.navigation.LocalGalleryStateSync
import net.kikin.nubecita.core.common.navigation.LocalSharedImageSurface
import net.kikin.nubecita.core.common.navigation.SharedImageSurfaces
import net.kikin.nubecita.core.common.navigation.postImageMemoryCacheKey
import net.kikin.nubecita.core.common.navigation.sharedImageModifier
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.thumbOrFullsize
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.spacing

/**
 * Renders a Bluesky `app.bsky.embed.images` embed.
 *
 * - **Single image (`items.size == 1`):** full-width with the layout
 *   height derived from the post's `aspectRatio` (width/height), clamped
 *   to `[MIN_ASPECT_RATIO, MAX_ASPECT_RATIO]` = `[2/3, 3/1]`.
 * - **Multi-image (`items.size > 1`):** delegates to M3's
 *   [HorizontalMultiBrowseCarousel].
 *
 * [postUri] opts the embed into the shared-element transition into the
 * full-screen media viewer and into the Coil memory-cache hand-off that makes
 * the viewer's first frame non-blank. Both also require the host surface to
 * provide `LocalSharedImageSurface`; cards on surfaces that cannot open the
 * viewer pass `postUri` and still render normally, just without either.
 */
@Composable
fun PostCardImageEmbed(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
    postUri: String? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
    cover: MediaCover? = null,
) {
    Box(modifier) {
        val effectiveImageClick = onImageClick.takeIf { cover == null }
        when (items.size) {
            0 -> Unit
            1 ->
                SingleImage(
                    image = items[0],
                    postUri = postUri,
                    onClick = effectiveImageClick?.let { handler -> { handler(0) } },
                    covered = cover != null,
                )
            else ->
                MultiImageCarousel(
                    items = items,
                    postUri = postUri,
                    onImageClick = effectiveImageClick,
                    covered = cover != null,
                )
        }
        if (cover != null) {
            MediaContentWarningCover(cover, Modifier.matchParentSize().clip(IMAGE_SHAPE))
        }
    }
}

/**
 * Builds the card's Coil request, tagging it with the surface-scoped memory
 * cache key the viewer reuses as its placeholder.
 *
 * The explicit key is only set when the surface participates — it replaces
 * Coil's default key, which includes the requested size, so setting it
 * unconditionally would make every surface share one entry per image at
 * whatever size happened to load first.
 */
@Composable
private fun rememberPostImageRequest(
    url: String?,
    postUri: String?,
    index: Int,
    covered: Boolean,
): ImageRequest {
    val context = LocalContext.current
    val surface = LocalSharedImageSurface.current
    return remember(url, postUri, index, covered, surface) {
        ImageRequest
            .Builder(context)
            .data(if (covered) null else url)
            .apply {
                if (!postUri.isNullOrBlank() && surface != SharedImageSurfaces.NONE) {
                    memoryCacheKey(postImageMemoryCacheKey(surface, postUri, index))
                }
            }.build()
    }
}

@Composable
private fun SingleImage(
    image: ImageUi,
    modifier: Modifier = Modifier,
    postUri: String? = null,
    onClick: (() -> Unit)? = null,
    covered: Boolean = false,
) {
    val imageRequest =
        rememberPostImageRequest(
            url = image.fullsizeUrl,
            postUri = postUri,
            index = 0,
            covered = covered,
        )
    val sharedBoundsModifier = sharedImageModifier(postUri = postUri, index = 0)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    NubecitaAsyncImage(
        model = imageRequest,
        contentDescription = image.altText,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(image.displayedAspectRatio())
                .clip(IMAGE_SHAPE)
                .then(sharedBoundsModifier)
                .then(clickModifier),
        contentScale = ContentScale.Crop,
    )
}

internal fun ImageUi.displayedAspectRatio(): Float = (aspectRatio ?: FALLBACK_ASPECT_RATIO).coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)

@Composable
private fun MultiImageCarousel(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
    postUri: String? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
    covered: Boolean = false,
) {
    val state = rememberCarouselState(itemCount = { items.size })
    val gallerySync = LocalGalleryStateSync.current

    // Consuming the hand-off WRITES state, so it belongs in the effect, not in
    // the composable body — a write to state this composable also reads would
    // invalidate it mid-composition.
    LaunchedEffect(postUri, items.size) {
        if (postUri != null) {
            val syncedIndex = gallerySync.consumeActiveIndex(postUri)
            if (syncedIndex != null && syncedIndex in items.indices && state.currentItem != syncedIndex) {
                state.scrollToItem(syncedIndex)
            }
        }
    }

    // The carousel sits inside the feed's horizontally-paged tab host. Without
    // this, a swipe that runs past the carousel's last item leaks the leftover
    // delta to the parent pager and switches tabs mid-gesture.
    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset =
                    if (source == NestedScrollSource.UserInput) {
                        Offset(available.x, 0f)
                    } else {
                        Offset.Zero
                    }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity = Velocity(available.x, 0f)
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(EMBED_HEIGHT)
                .nestedScroll(nestedScrollConnection),
    ) {
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = CAROUSEL_PREFERRED_ITEM_WIDTH,
            modifier = Modifier.fillMaxSize(),
            itemSpacing = EMBED_GAP,
        ) { index ->
            val image = items[index]
            val clickModifier =
                if (onImageClick != null) Modifier.clickable { onImageClick(index) } else Modifier
            val imageRequest =
                rememberPostImageRequest(
                    url = image.carouselSlideUrl,
                    postUri = postUri,
                    index = index,
                    covered = covered,
                )
            val sharedBoundsModifier = sharedImageModifier(postUri = postUri, index = index)

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .maskClip(IMAGE_SHAPE)
                        .then(sharedBoundsModifier)
                        .then(clickModifier),
            ) {
                NubecitaAsyncImage(
                    model = imageRequest,
                    contentDescription = image.altText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        if (items.size > 1) {
            CarouselCountPill(
                state = state,
                total = items.size,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(MaterialTheme.spacing.s2),
            )
        }
    }
}

/**
 * The `3/5` position pill.
 *
 * Its own composable so that reading `CarouselState.currentItem` — which is
 * `pagerState.currentPage`, and so changes on every page crossing — invalidates
 * only the pill. Read inline in [MultiImageCarousel] it would recompose the
 * whole carousel subtree, shared-element registrations included, on every swipe.
 */
@Composable
private fun CarouselCountPill(
    state: CarouselState,
    total: Int,
    modifier: Modifier = Modifier,
) {
    NubecitaOverlayPill(
        text = "${state.currentItem + 1}/$total",
        modifier = modifier,
    )
}

private val EMBED_HEIGHT: Dp = 240.dp
private val EMBED_GAP: Dp = 4.dp
private val CAROUSEL_PREFERRED_ITEM_WIDTH: Dp = 220.dp
private val IMAGE_SHAPE = RoundedCornerShape(16.dp)

private val ImageUi.carouselSlideUrl: String
    get() = thumbOrFullsize()

internal const val MIN_ASPECT_RATIO: Float = 2f / 3f
internal const val MAX_ASPECT_RATIO: Float = 3f / 1f
internal const val FALLBACK_ASPECT_RATIO: Float = 1f

@Preview(name = "Image embed — single landscape (3:2)", showBackground = true)
@Composable
private fun PostCardImageEmbedSingleLandscapePreview() {
    NubecitaTheme {
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 1.5f)))
    }
}

@Preview(name = "Image embed — single portrait (4:5)", showBackground = true)
@Composable
private fun PostCardImageEmbedSinglePortraitPreview() {
    NubecitaTheme {
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 0.8f)))
    }
}

@Preview(name = "Image embed — single ultra-tall (9:16, clamps)", showBackground = true)
@Composable
private fun PostCardImageEmbedSingleUltraTallPreview() {
    NubecitaTheme {
        PostCardImageEmbed(items = persistentListOf(previewImage(0, aspectRatio = 9f / 16f)))
    }
}

@Preview(name = "Image embed — single null aspect (fallback square)", showBackground = true)
@Composable
private fun PostCardImageEmbedSingleNullAspectPreview() {
    NubecitaTheme {
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
        fullsizeUrl = "https://example.com/placeholder/$index.jpg",
        thumbUrl = "https://example.com/placeholder/$index.jpg",
        altText = "Placeholder image $index",
        aspectRatio = aspectRatio,
    )
