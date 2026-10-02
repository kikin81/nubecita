package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.kikin.nubecita.designsystem.component.NubecitaOverlayStatButton
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * One cell of the vertical video feed's right-hand action rail: an icon with an
 * optional count beneath it, backed by a contrast-safe scrim treatment over media.
 *
 * Delegates to `:designsystem`'s [NubecitaOverlayStatButton] (nubecita-6rdb.18).
 */
@Composable
internal fun VideoRailAction(
    icon: NubecitaIconName,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Long? = null,
    active: Boolean = false,
    toggleable: Boolean = false,
    activeColor: Color = Color.White,
    testTag: String? = null,
) {
    NubecitaOverlayStatButton(
        icon = icon,
        accessibilityLabel = accessibilityLabel,
        onClick = onClick,
        modifier = modifier,
        count = count,
        active = active,
        toggleable = toggleable,
        activeColor = activeColor,
        testTag = testTag,
    )
}
