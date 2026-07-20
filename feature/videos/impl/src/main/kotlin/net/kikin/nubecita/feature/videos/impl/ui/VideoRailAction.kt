package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * One cell of the vertical video feed's right-hand action rail: an icon with an
 * optional count beneath it, drawn white over the video.
 *
 * `:designsystem`'s `PostStat` is `internal` and lays out horizontally, so it
 * cannot be reused here — but the accessibility contract is deliberately
 * identical to it:
 *
 * - **[toggleable] = true** (like, repost, mute) → `Modifier.toggleable(role =
 *   Role.Switch)`, with the label carried as the icon's `contentDescription`.
 *   `toggleable` accepts no label parameter, so an `onClickLabel` here would be
 *   silently dropped. TalkBack announces "<label>, switch, on/off".
 * - **[toggleable] = false** (reply, share) → `Modifier.clickable(role =
 *   Role.Button, onClickLabel = …)`, and the icon stays decorative so the label
 *   isn't announced twice. TalkBack announces "Double-tap to <label>".
 *
 * [accessibilityLabel] is therefore always the plain **noun** ("Like", "Mute"),
 * never the inverse verb ("Unlike", "Unmute") — on a toggle the state comes from
 * the switch semantics, not from the wording.
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
    val interactionModifier =
        if (toggleable) {
            Modifier.toggleable(
                value = active,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
        } else {
            Modifier.clickable(
                role = Role.Button,
                onClickLabel = accessibilityLabel,
                onClick = onClick,
            )
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RAIL_LABEL_GAP),
        modifier =
            modifier
                .clip(CircleShape)
                .then(interactionModifier)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .padding(horizontal = RAIL_CELL_PADDING, vertical = RAIL_CELL_PADDING),
    ) {
        NubecitaIcon(
            name = icon,
            contentDescription = if (toggleable) accessibilityLabel else null,
            filled = active,
            tint = if (active) activeColor else Color.White,
            opticalSize = RAIL_ICON_SIZE,
        )
        if (count != null) {
            Text(
                text = rememberCompactCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

private val RAIL_ICON_SIZE = 28.dp
private val RAIL_CELL_PADDING = 8.dp
private val RAIL_LABEL_GAP = 4.dp
