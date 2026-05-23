package net.kikin.nubecita.feature.profile.impl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage

/**
 * Circular avatar with an initials fallback when [avatarUrl] is null
 * (or while loading / on error — `NubecitaAsyncImage` handles that
 * via its placeholder painter).
 *
 * Mirrors the pattern in `:feature:chats:impl/ui/ConvoListItem.kt`'s
 * private `Avatar` composable: a hue-derived background derived
 * deterministically from a stable seed (preferring [displayName] over
 * [handle]) + the seed's first letter-or-digit. Caller supplies size
 * via [modifier] (e.g. `Modifier.size(88.dp)`) and the text style
 * that fits that size via [initialTextStyle].
 *
 * The camera-icon edit badge that previously overlaid the header
 * avatar was dropped — it had no tap behavior in v1, and rendering
 * it inside an otherwise-empty placeholder avatar was the entire
 * thing the user saw on a not-yet-loaded profile. When avatar
 * editing actually ships, restore the overlay there.
 */
@Composable
internal fun SettingsAvatar(
    handle: String,
    displayName: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    initialTextStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val seed = displayName?.takeIf { it.isNotBlank() } ?: handle
    val hue =
        remember(seed) {
            // hashCode().rem(360) can be negative; normalize to [0, 360).
            ((seed.hashCode().rem(360) + 360).rem(360)).toFloat()
        }
    val hueColor = Color.hsv(hue = hue, saturation = 0.5f, value = 0.55f)
    val initial =
        seed
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?: '?'

    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(hueColor),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            NubecitaAsyncImage(
                model = avatarUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initial.toString(),
                color = if (hueColor.luminance() > 0.5f) Color.Black else Color.White,
                style = initialTextStyle,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
