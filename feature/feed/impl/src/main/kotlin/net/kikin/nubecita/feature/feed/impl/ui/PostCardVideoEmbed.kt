@file:androidx.annotation.OptIn(UnstableApi::class)

package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.extendedShape
import net.kikin.nubecita.designsystem.semanticColors
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.feed.impl.R
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.PlaybackHint
import java.util.Locale

/**
 * Renders a Bluesky video embed as a static poster card with an optional
 * duration chip. **Phase B** of the openspec change
 * `add-feature-feed-video-embeds` — no inline playback, no `PlayerSurface`,
 * no mute icon. Phase C extends this composable to add the autoplay-muted
 * `PlayerSurface` overlay + mute toggle when bound by the
 * `FeedVideoPlayerCoordinator` (separate overload below).
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
                .clip(MaterialTheme.shapes.large),
    ) {
        PosterLayer(video = video)
        DurationChipIfPresent(durationSeconds = video.durationSeconds)
    }
}

/**
 * Phase-C autoplay overload. Layers the `PlayerSurface` + mute icon +
 * resume overlay on top of the phase-B poster card when the
 * [coordinator] is bound to [postId]. Falls back to the phase-B render
 * under `LocalInspectionMode.current` since layoutlib can't construct
 * `PlayerSurface`.
 *
 * **Bound vs. not.** Reads `coordinator.boundPostId` and renders the
 * poster baseline either way. The bound card additionally renders the
 * `PlayerSurface` over the poster (the surface paints the video frame
 * on top of the still poster — no explicit cross-fade is performed;
 * the surface becomes opaque once the player produces its first
 * frame), the mute icon overlay, and — if `playbackHint == FocusLost`
 * — the "tap to resume" affordance. Only the bound card subscribes
 * to `coordinator.isUnmuted` + `coordinator.playbackHint`, so
 * flipping the mute state on the bound card does NOT recompose the
 * off-screen non-bound video cards in the LazyColumn.
 *
 * **Tap targets.** The mute icon and resume overlay each consume
 * their own `clickable` so taps on those affordances do NOT propagate
 * to PostCard's outer `clickable { callbacks.onTap }`. Anywhere else
 * on the card body still navigates to detail.
 */
@Composable
internal fun PostCardVideoEmbed(
    video: EmbedUi.Video,
    postId: String,
    coordinator: FeedVideoPlayerCoordinator,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        // Inspection mode (IDE @Preview, screenshot tests) — render the
        // phase-B variant; layoutlib can't construct PlayerSurface.
        PostCardVideoEmbed(video = video, modifier = modifier)
    } else {
        PostCardVideoEmbedAutoplay(
            video = video,
            postId = postId,
            coordinator = coordinator,
            modifier = modifier,
        )
    }
}

/**
 * Phase-B overload for a quoted-post video. Adapts the
 * `QuotedEmbedUi.Video` (carried by a `QuotedPostUi`) to the same
 * field-set the parent `EmbedUi.Video` overload renders. Used by
 * the `:designsystem` `quotedVideoEmbedSlot` lambda when no
 * coordinator is available (preview / screenshot tests).
 */
@Composable
internal fun PostCardVideoEmbed(
    quotedVideo: QuotedEmbedUi.Video,
    modifier: Modifier = Modifier,
) {
    val asEmbedUiVideo = remember(quotedVideo) { quotedVideo.toEmbedUiVideo() }
    PostCardVideoEmbed(video = asEmbedUiVideo, modifier = modifier)
}

/**
 * Phase-C autoplay overload for a quoted-post video. Adapts to the
 * parent `EmbedUi.Video` shape and forwards to the existing autoplay
 * pipeline; the bind identity is the quoted post's AT URI (passed in
 * as [postId]) so the coordinator's "is this the same target?"
 * rebind logic naturally distinguishes parent and quoted videos.
 *
 * Inspection mode falls through to the phase-B overload as the
 * parent does — layoutlib can't construct `PlayerSurface`.
 */
@Composable
internal fun PostCardVideoEmbed(
    quotedVideo: QuotedEmbedUi.Video,
    postId: String,
    coordinator: FeedVideoPlayerCoordinator,
    modifier: Modifier = Modifier,
) {
    val asEmbedUiVideo = remember(quotedVideo) { quotedVideo.toEmbedUiVideo() }
    PostCardVideoEmbed(
        video = asEmbedUiVideo,
        postId = postId,
        coordinator = coordinator,
        modifier = modifier,
    )
}

/**
 * Adapter — converts a quoted-post video to the parent video
 * shape. Wrapper-type duplication on the data side (per the
 * compile-time recursion bound) becomes wrapper-type unification
 * here, where the renderer doesn't care which sealed type carries
 * the field-set. Memoized via `remember(quotedVideo)` at each call
 * site so the allocation lands once per visible quoted-video, not
 * per recomposition.
 */
private fun QuotedEmbedUi.Video.toEmbedUiVideo(): EmbedUi.Video =
    EmbedUi.Video(
        posterUrl = posterUrl,
        playlistUrl = playlistUrl,
        aspectRatio = aspectRatio,
        durationSeconds = durationSeconds,
        altText = altText,
    )

@Composable
private fun PostCardVideoEmbedAutoplay(
    video: EmbedUi.Video,
    postId: String,
    coordinator: FeedVideoPlayerCoordinator,
    modifier: Modifier = Modifier,
) {
    val boundPostId by coordinator.boundPostId.collectAsStateWithLifecycle()
    val isBoundHere = boundPostId == postId
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(video.aspectRatio)
                .clip(MaterialTheme.shapes.large),
    ) {
        // Poster (or gradient) is the base layer regardless of bind
        // state. When bound, the PlayerSurface paints over it; once
        // the player produces its first frame the surface becomes
        // opaque and the poster underneath is hidden.
        PosterLayer(video = video)
        if (isBoundHere) {
            // Subscribe to isUnmuted + playbackHint ONLY on the bound
            // card. Non-bound cards in the LazyColumn don't recompose
            // when the bound card flips mute state. Extracted into its
            // own composable so non-bound cards skip subscription.
            BoundOverlay(coordinator = coordinator)
        }
        DurationChipIfPresent(durationSeconds = video.durationSeconds)
    }
}

@Composable
private fun BoxScope.BoundOverlay(coordinator: FeedVideoPlayerCoordinator) {
    val isUnmuted by coordinator.isUnmuted.collectAsStateWithLifecycle()
    val playbackHint by coordinator.playbackHint.collectAsStateWithLifecycle()
    val onMuteClick = remember(coordinator) { { coordinator.toggleMute() } }
    val onResumeClick = remember(coordinator) { { coordinator.resume() } }

    PlayerSurface(
        player = coordinator.player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = Modifier.fillMaxSize(),
    )
    MuteIconOverlay(
        isUnmuted = isUnmuted,
        onClick = onMuteClick,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(MaterialTheme.spacing.s2),
    )
    if (playbackHint == PlaybackHint.FocusLost) {
        ResumeOverlay(
            onClick = onResumeClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BoxScope.PosterLayer(video: EmbedUi.Video) {
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
}

@Composable
private fun BoxScope.DurationChipIfPresent(durationSeconds: Int?) {
    // Smart-cast can't survive a cross-module nullable read; bind to a
    // local before the null check.
    val seconds = durationSeconds ?: return
    DurationChip(
        seconds = seconds,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(MaterialTheme.spacing.s2),
    )
}

@Composable
private fun MuteIconOverlay(
    isUnmuted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, label) =
        if (isUnmuted) {
            Icons.AutoMirrored.Outlined.VolumeUp to stringResource(R.string.postcard_video_mute)
        } else {
            Icons.AutoMirrored.Outlined.VolumeOff to stringResource(R.string.postcard_video_unmute)
        }
    Box(
        modifier =
            modifier
                .size(MaterialTheme.spacing.s8)
                .clip(MaterialTheme.extendedShape.pill)
                .background(MaterialTheme.semanticColors.videoOverlayScrim)
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.semanticColors.onVideoOverlay,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(MaterialTheme.spacing.s5),
        )
    }
}

@Composable
private fun ResumeOverlay(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resumeLabel = stringResource(R.string.postcard_video_resume)
    Box(
        modifier =
            modifier
                .background(MaterialTheme.semanticColors.videoOverlayScrimSubtle)
                .clickable(onClick = onClick)
                .semantics { contentDescription = resumeLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(MaterialTheme.extendedShape.pill)
                    .background(MaterialTheme.semanticColors.videoOverlayScrim)
                    .padding(
                        horizontal = MaterialTheme.spacing.s4,
                        vertical = MaterialTheme.spacing.s3,
                    ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.semanticColors.onVideoOverlay,
                    modifier = Modifier.size(MaterialTheme.spacing.s5),
                )
                Text(
                    text = resumeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.semanticColors.onVideoOverlay,
                )
            }
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
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.semanticColors.videoOverlayScrim)
                .padding(
                    horizontal = MaterialTheme.spacing.s2,
                    vertical = MaterialTheme.spacing.s1,
                ),
    ) {
        Text(
            text = formatDuration(seconds),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.semanticColors.onVideoOverlay,
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
