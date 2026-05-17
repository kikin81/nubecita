package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Stateless poster card for a Bluesky video embed on non-autoplay
 * surfaces (profile post list, postdetail thread). Renders the
 * [posterUrl] poster image (with a branded gradient fallback when
 * absent) under a centered play-arrow badge that signals the surface
 * is tappable. Tap routes the host to the fullscreen player.
 *
 * Counterpart to `:feature:feed:impl`'s `PostCardVideoEmbed` — the feed
 * version overlays a `PlayerSurface` + mute toggle for autoplay, which
 * is feature-internal because layoutlib can't construct `PlayerSurface`
 * and because the autoplay coordinator binds to a specific feed-only
 * shared player instance. This composable is the autoplay-free render
 * the rest of the app uses, hoisted into `:designsystem` so profile +
 * postdetail can share it without depending on `:feature:feed:impl`.
 *
 * **Aspect-ratio gate.** `Modifier.fillMaxWidth().aspectRatio(...)` is
 * applied on the outer `Box` before any image load begins — same lock
 * the feed's `PostCardVideoEmbed` uses to prevent a height jump when
 * the poster finishes loading and the LazyColumn would otherwise
 * reflow. Mappers guarantee `aspectRatio` is non-null (16:9 fallback)
 * so the field is required.
 *
 * **Accessibility.** When `altText` is non-null it propagates to the
 * outer Box as `contentDescription` so TalkBack announces the video
 * before the play affordance — even when the gradient fallback is
 * showing instead of a real poster. Role.Button is attached to the
 * tap target so TalkBack reads "double-tap to activate".
 */
@Composable
fun VideoPosterEmbed(
    posterUrl: String?,
    aspectRatio: Float,
    altText: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fall back to a generic label when the lexicon's altText is null so
    // TalkBack never lands on an unlabeled `Role.Button`. Resolved at
    // composition time (rather than inside the semantics block) so locale
    // changes participate in recomposition.
    val resolvedDescription = altText ?: stringResource(R.string.video_poster_default_content_description)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onTap)
                .semantics { contentDescription = resolvedDescription },
    ) {
        if (posterUrl != null) {
            NubecitaAsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            GradientPosterFallback(modifier = Modifier.fillMaxSize())
        }
        PlayBadge(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun GradientPosterFallback(modifier: Modifier = Modifier) {
    val top = MaterialTheme.colorScheme.surfaceContainerHigh
    val bottom = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier =
            modifier.background(
                brush = Brush.verticalGradient(colors = listOf(top, bottom)),
            ),
    )
}

@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(BADGE_SIZE)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(BADGE_ICON_SIZE),
        )
    }
}

private val BADGE_SIZE = 56.dp
private val BADGE_ICON_SIZE = 32.dp

@Preview(name = "VideoPosterEmbed — with poster", showBackground = true)
@Composable
private fun VideoPosterEmbedWithPosterPreview() {
    NubecitaTheme {
        VideoPosterEmbed(
            posterUrl = "https://example.com/poster.jpg",
            aspectRatio = 16f / 9f,
            altText = "A clip of a cat",
            onTap = {},
        )
    }
}

@Preview(name = "VideoPosterEmbed — no poster", showBackground = true)
@Composable
private fun VideoPosterEmbedNoPosterPreview() {
    NubecitaTheme {
        VideoPosterEmbed(
            posterUrl = null,
            aspectRatio = 16f / 9f,
            altText = null,
            onTap = {},
        )
    }
}

@Preview(name = "VideoPosterEmbed — portrait", showBackground = true)
@Composable
private fun VideoPosterEmbedPortraitPreview() {
    NubecitaTheme {
        VideoPosterEmbed(
            posterUrl = null,
            aspectRatio = 9f / 16f,
            altText = null,
            onTap = {},
        )
    }
}
