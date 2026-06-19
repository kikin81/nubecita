package net.kikin.nubecita.feature.settings.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * [hue] is a precomputed 0–359 value supplied by the caller (the
 * SettingsViewModel derives it via
 * [net.kikin.nubecita.core.common.avatar.avatarHueFor] so it stays identical
 * to the same user's Profile / Chats avatar — the three feature
 * surfaces share the same algorithm and seed from `:core:common`).
 * Computing it locally from displayName / handle would drift from the
 * DID-keyed convention used by sibling surfaces AND would visibly
 * re-paint the disc when the display name fetch resolves after the
 * initial render — both surfaced as Copilot review feedback on PR #287.
 *
 * [initialSeed] is the string the first letter-or-digit is taken
 * from for the fallback initial. Callers typically pass
 * `displayName ?: handle` so the letter reflects the user's
 * preferred display once the profile fetch lands, while [hue]
 * stays stable.
 *
 * Caller supplies size via [modifier] (e.g.
 * `Modifier.size(88.dp)`) and the text style that fits that size
 * via [initialTextStyle].
 */
@Composable
internal fun SettingsAvatar(
    hue: Int,
    initialSeed: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    initialTextStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val hueColor =
        Color.hsv(
            hue = hue.toFloat().coerceIn(0f, 360f),
            saturation = 0.5f,
            value = 0.55f,
        )
    val initial =
        initialSeed
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
