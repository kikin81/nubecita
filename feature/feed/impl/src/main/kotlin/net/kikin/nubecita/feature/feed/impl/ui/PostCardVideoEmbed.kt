package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import java.util.Locale

/**
 * Renders a Bluesky video embed as a static poster card with an optional
 * duration chip. **Phase B** of the openspec change
 * `add-feature-feed-video-embeds` — no inline playback, no `PlayerSurface`,
 * no mute icon. Phase C (`nubecita-sbc.4`) extends this composable to add
 * the autoplay-muted `PlayerSurface` overlay + mute toggle when bound by
 * the `FeedVideoPlayerCoordinator`.
 *
 * **Aspect-ratio gate.** The outer `Box` applies
 * `Modifier.fillMaxWidth().aspectRatio(video.aspectRatio)` BEFORE
 * `NubecitaAsyncImage` begins loading. This locks the LazyColumn's
 * measurement on first composition so the poster's eventual resolution
 * never triggers a height jump that propagates into a visible
 * scroll-position shift. (Mapper guarantees `aspectRatio` is non-null —
 * 16:9 fallback when the lexicon's optional field is absent.)
 *
 * **Tap target.** Card-body tap is handled by `PostCard`'s outer
 * `clickable { callbacks.onTap(post) }` — no special handling here. The
 * feed feature wires `onTap` to navigate to the post-detail screen
 * (separate epic), where the full player + controls live.
 *
 * **Duration chip.** The Bluesky lexicon does NOT currently expose a
 * duration field on `app.bsky.embed.video#view`, so v1 mapper passes
 * `durationSeconds = null` for every post. The chip code path is gated
 * on `video.durationSeconds != null` and stays in place for a future
 * phase that sources duration (lexicon evolution or HLS manifest
 * parsing). Screenshot tests cover the chip visuals against synthetic
 * non-null inputs to guard the layout.
 */
@Composable
internal fun PostCardVideoEmbed(
    video: EmbedUi.Video,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(video.aspectRatio)
                .clip(VIDEO_CARD_SHAPE),
    ) {
        if (video.posterUrl != null) {
            NubecitaAsyncImage(
                model = video.posterUrl,
                contentDescription = video.altText,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            GradientPosterFallback(
                altText = video.altText,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Smart-cast can't survive a cross-module nullable read; bind to a
        // local before the null check.
        val durationSeconds = video.durationSeconds
        if (durationSeconds != null) {
            DurationChip(
                seconds = durationSeconds,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
            )
        }
    }
}

/**
 * Branded fallback for the missing-thumbnail case. A linear gradient over
 * the surface tones picks up theme colors so the empty state feels native
 * rather than a debug white box. When the video carries an `altText`, it
 * is attached as a TalkBack content description on the gradient surface
 * so screen-reader announcements parity the poster branch (where
 * `NubecitaAsyncImage`'s `contentDescription` parameter handles it).
 */
@Composable
private fun GradientPosterFallback(
    altText: String?,
    modifier: Modifier = Modifier,
) {
    val top = MaterialTheme.colorScheme.surfaceContainerHigh
    val bottom = MaterialTheme.colorScheme.surfaceContainerHighest
    val a11yModifier =
        if (altText != null) {
            Modifier.semantics { contentDescription = altText }
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .then(a11yModifier)
                .background(
                    brush = Brush.verticalGradient(colors = listOf(top, bottom)),
                ),
    )
}

@Composable
private fun DurationChip(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(DURATION_CHIP_SHAPE)
                .background(DURATION_CHIP_SCRIM)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = formatDuration(seconds),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/**
 * `m:ss` for clips under 1 hour, `h:mm:ss` for longer. The format string
 * is fed through [Locale.ROOT] so digit shaping stays ASCII regardless
 * of the device or CI locale — without that, locales like `ar-SA` would
 * render Eastern Arabic numerals (`٠-٩`) and our screenshot baselines
 * would diverge by environment.
 */
private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

private val VIDEO_CARD_SHAPE = RoundedCornerShape(16.dp)
private val DURATION_CHIP_SHAPE = RoundedCornerShape(8.dp)
private val DURATION_CHIP_SCRIM = Color(0xCC000000)

@Preview(name = "Video — with poster", showBackground = true)
@Composable
private fun PostCardVideoEmbedWithPosterPreview() {
    NubecitaTheme {
        PostCardVideoEmbed(video = previewVideo())
    }
}

@Preview(name = "Video — no poster (gradient)", showBackground = true)
@Composable
private fun PostCardVideoEmbedNoPosterPreview() {
    NubecitaTheme {
        PostCardVideoEmbed(video = previewVideo(posterUrl = null))
    }
}

@Preview(name = "Video — short duration (0:32)", showBackground = true)
@Composable
private fun PostCardVideoEmbedShortDurationPreview() {
    NubecitaTheme {
        PostCardVideoEmbed(video = previewVideo(durationSeconds = 32))
    }
}

@Preview(name = "Video — long duration (1:23:45)", showBackground = true)
@Composable
private fun PostCardVideoEmbedLongDurationPreview() {
    NubecitaTheme {
        PostCardVideoEmbed(video = previewVideo(durationSeconds = 3600 + 23 * 60 + 45))
    }
}

internal fun previewVideo(
    posterUrl: String? = "https://example.com/poster.jpg",
    aspectRatio: Float = 16f / 9f,
    durationSeconds: Int? = null,
    altText: String? = null,
): EmbedUi.Video =
    EmbedUi.Video(
        posterUrl = posterUrl,
        playlistUrl = "https://video.bsky.app/preview/playlist.m3u8",
        aspectRatio = aspectRatio,
        durationSeconds = durationSeconds,
        altText = altText,
    )
