package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Inline animated GIF — an `app.bsky.embed.external` whose URL is an
 * `image/gif` (Bluesky's GIF picker: Klipy/Tenor/Giphy). Coil's
 * `AnimatedImageDecoder` (registered in `CoilModule`) auto-loops it.
 *
 * Deliberately NOT the video pipeline: the app's single shared ExoPlayer can
 * only drive one video, so N GIFs in a thread would freeze. Each
 * [PostCardGifEmbed] is an independent Coil drawable, and a GIF stops animating
 * the moment its LazyColumn item leaves composition — so a GIF-heavy thread only
 * pays for the few on screen.
 *
 * [aspectRatio] reserves exact layout space when known (no scroll jump). When
 * unknown (a bare `.gif` with no dimensions), the height is reserved between
 * [MIN_GIF_HEIGHT] and [MAX_GIF_HEIGHT] so the box is never measured to 0dp
 * (invisible) before the frame loads and then jumps. Klipy/Tenor URLs carry
 * `ww`/`hh`, so the known path is the common one.
 */
@Composable
fun PostCardGifEmbed(
    gifUrl: String,
    aspectRatio: Float?,
    alt: String?,
    modifier: Modifier = Modifier,
    cover: MediaCover? = null,
) {
    val sized =
        modifier
            .fillMaxWidth()
            .let { base ->
                if (aspectRatio != null) {
                    base.aspectRatio(aspectRatio)
                } else {
                    base.heightIn(min = MIN_GIF_HEIGHT, max = MAX_GIF_HEIGHT)
                }
            }.clip(RoundedCornerShape(16.dp))
    Box(sized) {
        NubecitaAsyncImage(
            // Covered → no model, so Coil never fetches/decodes the GIF.
            model = if (cover != null) null else gifUrl,
            contentDescription = alt,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (cover != null) {
            MediaContentWarningCover(cover, Modifier.matchParentSize())
        }
    }
}

private val MIN_GIF_HEIGHT = 160.dp
private val MAX_GIF_HEIGHT = 400.dp

@Preview(name = "GIF embed", showBackground = true)
@Composable
private fun PostCardGifEmbedPreview() {
    // Inspection mode renders the AsyncImage placeholder (no network in
    // layoutlib); this preview pins the box layout. Wrapped in NubecitaTheme
    // because NubecitaAsyncImage reads MaterialTheme.colorScheme.
    NubecitaTheme(dynamicColor = false) {
        PostCardGifEmbed(
            gifUrl = "https://static.klipy.com/example.gif",
            aspectRatio = 1.2f,
            alt = "example gif",
        )
    }
}
