package net.kikin.nubecita.designsystem.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.navigation.LocalAnimatedVisibilityScope
import net.kikin.nubecita.core.common.navigation.LocalGalleryStateSync
import net.kikin.nubecita.core.common.navigation.LocalSharedTransitionScope
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
 */
@Composable
fun PostCardImageEmbed(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
    postUri: String? = null,
    contextKey: String? = null,
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
                    contextKey = contextKey,
                    onClick = effectiveImageClick?.let { handler -> { handler(0) } },
                    covered = cover != null,
                )
            else ->
                MultiImageCarousel(
                    items = items,
                    postUri = postUri,
                    contextKey = contextKey,
                    onImageClick = effectiveImageClick,
                    covered = cover != null,
                )
        }
        if (cover != null) {
            MediaContentWarningCover(cover, Modifier.matchParentSize().clip(IMAGE_SHAPE))
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SingleImage(
    image: ImageUi,
    modifier: Modifier = Modifier,
    postUri: String? = null,
    contextKey: String? = null,
    onClick: (() -> Unit)? = null,
    covered: Boolean = false,
) {
    val context = LocalContext.current
    val imageRequest =
        remember(image.fullsizeUrl, covered, postUri) {
            val url = if (covered) null else image.fullsizeUrl
            ImageRequest
                .Builder(context)
                .data(url)
                .apply {
                    if (postUri != null) {
                        memoryCacheKey("coil_${postUri}_0")
                    }
                }.build()
        }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val sharedBoundsModifier =
        if (
            sharedTransitionScope != null &&
            animatedVisibilityScope != null &&
            postUri != null
        ) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState =
                        rememberSharedContentState(
                            key = "shared_image_${contextKey ?: ""}_${postUri}_0",
                        ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
                )
            }
        } else {
            Modifier
        }

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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MultiImageCarousel(
    items: ImmutableList<ImageUi>,
    modifier: Modifier = Modifier,
    postUri: String? = null,
    contextKey: String? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
    covered: Boolean = false,
) {
    val state = rememberCarouselState(itemCount = { items.size })
    val gallerySync = LocalGalleryStateSync.current

    if (postUri != null) {
        val syncedIndex = gallerySync.getActiveIndex(postUri)
        LaunchedEffect(syncedIndex) {
            if (syncedIndex != null && syncedIndex in items.indices && state.currentItem != syncedIndex) {
                state.scrollToItem(syncedIndex)
            }
        }
    }

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
            }
        }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

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

            val context = LocalContext.current
            val imageRequest =
                remember(image.carouselSlideUrl, covered, postUri, index) {
                    val url = if (covered) null else image.carouselSlideUrl
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .apply {
                            if (postUri != null) {
                                memoryCacheKey("coil_${postUri}_$index")
                            }
                        }.build()
                }

            val sharedBoundsModifier =
                if (
                    sharedTransitionScope != null &&
                    animatedVisibilityScope != null &&
                    postUri != null
                ) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState =
                                rememberSharedContentState(
                                    key = "shared_image_${contextKey ?: ""}_${postUri}_$index",
                                ),
                            animatedVisibilityScope = animatedVisibilityScope,
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop),
                        )
                    }
                } else {
                    Modifier
                }

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
            NubecitaOverlayPill(
                text = "${state.currentItem + 1}/${items.size}",
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(MaterialTheme.spacing.s2),
            )
        }
    }
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
