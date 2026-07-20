package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.videos.impl.VideoFeedTestTags

/**
 * One page of the vertical video feed.
 *
 * The video itself is NOT here: a single persistent surface lives behind the
 * pager and is shared by every page. This composable is the poster layer that
 * renders *over* that surface and fades out once the active player has a frame
 * — so a page is never black, and the swap between outgoing video, poster and
 * incoming video is covered at every instant.
 *
 * [aspectRatio] must be the same ratio the surface is using, or the crossfade
 * reads as a jump rather than a dissolve.
 *
 * Overlay chrome (author, caption, interactions, mute) lands in PR2 and composes
 * into this Box above the poster.
 */
@Composable
internal fun VideoFeedPage(
    posterUrl: String?,
    aspectRatio: Float,
    posterAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // graphicsLayer (not Modifier.alpha) so a crossfade only re-runs the
        // layer block — no recomposition or relayout per frame at 120hz.
        val posterModifier =
            Modifier
                .aspectRatio(aspectRatio)
                .graphicsLayer { alpha = posterAlpha }
                .testTag(VideoFeedTestTags.POSTER)
        if (posterUrl == null) {
            // Spec D4: a missing poster degrades to flat black, NOT to
            // NubecitaAsyncImage's fallback painter. That painter is a light
            // surfaceContainerHighest tile, which on this always-black video
            // canvas would flash a grey rectangle exactly where the poster
            // layer exists to prevent one.
            Box(posterModifier.background(Color.Black))
        } else {
            NubecitaAsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = posterModifier,
                contentScale = ContentScale.Fit,
            )
        }
    }
}
