package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import net.kikin.nubecita.designsystem.LocalGifAutoplayEnabled
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

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
    val gifAutoplayEnabled = LocalGifAutoplayEnabled.current
    // Reset whenever autoplay flips or the URL changes: a card recycled to a
    // different GIF must not inherit the previous one's "user tapped play".
    var playRequested by remember(gifUrl, gifAutoplayEnabled) { mutableStateOf(false) }
    val animate = gifAutoplayEnabled || playRequested
    val context = LocalContext.current
    val playLabel = stringResource(R.string.postcard_gif_play)
    val model =
        remember(gifUrl, cover, animate, context) {
            when {
                // Covered → no model, so Coil never fetches or decodes the GIF.
                cover != null -> null
                animate -> gifUrl
                else ->
                    ImageRequest
                        .Builder(context)
                        .data(gifUrl)
                        // Per-request, so the ImageLoader keeps its animated
                        // decoder for every other GIF on screen.
                        //
                        // BitmapFactoryDecoder rather than StaticImageDecoder:
                        // the latter is `ImageDecoder`-backed and API 29+, and
                        // minSdk here is 28. BitmapFactory decodes a GIF to its
                        // first frame on every supported level.
                        .decoderFactory(BitmapFactoryDecoder.Factory())
                        // A distinct memory-cache key, because the decoder is
                        // NOT part of Coil's default key — that is the URL. The
                        // animated request for the same GIF would otherwise be
                        // handed this still bitmap straight from the cache, and
                        // tapping to play would silently do nothing. Verified
                        // on device: without this the badge disappears (the
                        // state flips) while the frame never moves.
                        .memoryCacheKey("$gifUrl#static")
                        .build()
            }
        }
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
            .let { base ->
                // Tap starts this GIF. Skipped while covered so a tap can't
                // bypass the warning, and skipped when it is already animating
                // so the card keeps falling through to the host's own tap.
                if (!animate && cover == null) {
                    base.clickable(
                        // A verb phrase, because TalkBack reads it as
                        // "double-tap to <label>". Without it the card announces
                        // a bare "double-tap to activate" and the badge — which
                        // deliberately carries no description of its own —
                        // leaves nothing to say what activating would do.
                        onClickLabel = playLabel,
                        role = Role.Button,
                    ) { playRequested = true }
                } else {
                    base
                }
            }
    Box(sized) {
        // The image layer stays composed (with a null model) while covered
        // rather than being skipped: the cover is clipped to the same 16dp
        // rounded corners, and its antialiased edge blends with whatever is
        // behind it. Removing the layer underneath measurably changes those
        // corner pixels (~300 px, up to 51/255 in dark mode), so "the cover is
        // opaque, nothing shows through" is true of the fill but not the edge.
        NubecitaAsyncImage(
            model = model,
            // Blank alt stays null so the image is decorative: an empty
            // contentDescription makes TalkBack announce nothing useful where
            // saying nothing at all is correct.
            contentDescription = alt?.takeIf { it.isNotBlank() },
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (cover != null) {
            MediaContentWarningCover(cover, Modifier.matchParentSize())
        } else if (!animate) {
            MediaPlayBadge(Modifier.align(Alignment.Center))
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
