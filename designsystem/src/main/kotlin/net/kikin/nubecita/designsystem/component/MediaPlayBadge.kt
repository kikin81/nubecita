package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.semanticColors
import net.kikin.nubecita.designsystem.spacing

/**
 * Centred play badge drawn over a media poster that is **not** playing on its
 * own — a video with autoplay off, or a GIF showing its first frame.
 *
 * Without it, a stopped video or a still GIF is indistinguishable from a plain
 * image, which makes the autoplay setting read as "media removed" rather than
 * "media waits for you". That is the whole reason this exists.
 *
 * Deliberately **not** clickable and carrying **no** `contentDescription`: every
 * host already installs a tap on the whole media Box and owns the semantics for
 * it. A second target stacked on top would swallow the gesture on the one spot
 * users aim for, and a second description would make TalkBack read the card
 * twice.
 *
 * Painted with the `videoOverlayScrim` / `onVideoOverlay` semantic tokens —
 * fixed black-on-white rather than scheme colours, because it sits over
 * arbitrary photography where a themed tint has no contrast guarantee at all.
 * The same pair paints the mute icon on the very same video card, so the two
 * overlays cannot drift apart.
 */
@Composable
fun MediaPlayBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(MaterialTheme.spacing.s12)
                .background(color = MaterialTheme.semanticColors.videoOverlayScrim, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.semanticColors.onVideoOverlay,
            modifier = Modifier.size(MaterialTheme.spacing.s7),
        )
    }
}

@Preview(name = "Media play badge", showBackground = true)
@Composable
private fun MediaPlayBadgePreview() {
    NubecitaTheme(dynamicColor = false) {
        MediaPlayBadge()
    }
}
