package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Baselines for the media overlay controls.
 *
 * The axis swept here is the **backdrop**, not light/dark. `videoOverlayScrim`
 * and `onVideoOverlay` are theme-invariant by design — the contrast target is
 * user-supplied video pixels, not the page background — so light and dark
 * renders would be byte-identical and prove nothing.
 *
 * White is the case that matters. Bare white-on-video controls disappeared
 * against a bright frame, which is the defect this component exists to fix
 * (nubecita-6rdb.16), so a white backdrop is the one a regression would show up
 * on first. Black and mid-tone are included so a scrim that is too *heavy*
 * is also visible, not just one that is too light.
 */
@Composable
private fun OverBackdrop(
    backdrop: Color,
    content: @Composable () -> Unit,
) {
    NubecitaTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.background(backdrop).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun IconButtonRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NubecitaOverlayIconButton(
            icon = NubecitaIconName.Close,
            accessibilityLabel = "Back",
            onClick = {},
        )
        NubecitaOverlayIconButton(
            icon = NubecitaIconName.VolumeUp,
            accessibilityLabel = "Mute",
            onClick = {},
            toggleable = true,
        )
        NubecitaOverlayIconButton(
            icon = NubecitaIconName.VolumeOff,
            accessibilityLabel = "Mute",
            onClick = {},
            active = true,
            toggleable = true,
        )
    }
}

@Composable
private fun StatButtonRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NubecitaOverlayStatButton(
            icon = NubecitaIconName.Favorite,
            accessibilityLabel = "Like",
            onClick = {},
            count = 4_600,
            toggleable = true,
        )
        NubecitaOverlayStatButton(
            icon = NubecitaIconName.Repeat,
            accessibilityLabel = "Repost",
            onClick = {},
            count = 445,
            toggleable = true,
        )
        NubecitaOverlayStatButton(
            icon = NubecitaIconName.ChatBubble,
            accessibilityLabel = "Reply",
            onClick = {},
            count = 121,
        )
        // No count — proves the capsule degrades to a circle rather than
        // rendering a squashed pill, so a counted and an uncounted rail cell
        // stay visually consistent.
        NubecitaOverlayStatButton(
            icon = NubecitaIconName.IosShare,
            accessibilityLabel = "Share",
            onClick = {},
        )
    }
}

@PreviewTest
@Preview(name = "overlay controls — over white")
@Composable
private fun OverlayControlsOverWhitePreview() {
    OverBackdrop(Color.White) {
        IconButtonRow()
        StatButtonRow()
    }
}

@PreviewTest
@Preview(name = "overlay controls — over black")
@Composable
private fun OverlayControlsOverBlackPreview() {
    OverBackdrop(Color.Black) {
        IconButtonRow()
        StatButtonRow()
    }
}

@PreviewTest
@Preview(name = "overlay controls — over mid-tone")
@Composable
private fun OverlayControlsOverMidTonePreview() {
    OverBackdrop(Color(0xFF7F7F7F)) {
        IconButtonRow()
        StatButtonRow()
    }
}
