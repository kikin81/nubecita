package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
 * [aspectRatio] reserves exact layout space when known (no scroll jump);
 * otherwise the height is capped at [MAX_GIF_HEIGHT].
 */
@Composable
fun PostCardGifEmbed(
    gifUrl: String,
    aspectRatio: Float?,
    alt: String?,
    modifier: Modifier = Modifier,
) {
    val sized =
        modifier
            .fillMaxWidth()
            .let { base ->
                if (aspectRatio != null) base.aspectRatio(aspectRatio) else base.heightIn(max = MAX_GIF_HEIGHT)
            }.clip(RoundedCornerShape(16.dp))
    NubecitaAsyncImage(
        model = gifUrl,
        contentDescription = alt,
        modifier = sized,
        contentScale = ContentScale.Crop,
    )
}

private val MAX_GIF_HEIGHT = 400.dp

@Preview(name = "GIF embed", showBackground = true)
@Composable
private fun PostCardGifEmbedPreview() {
    // Inspection mode renders the AsyncImage placeholder (no network in
    // layoutlib); this preview pins the aspect-ratio box layout.
    PostCardGifEmbed(
        gifUrl = "https://static.klipy.com/example.gif",
        aspectRatio = 1.2f,
        alt = "example gif",
    )
}
