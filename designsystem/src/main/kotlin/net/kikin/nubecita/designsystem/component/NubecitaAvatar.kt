package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.avatar.avatarHueFor
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Identity fallback for an avatarless person: a deterministic hue disc with the
 * person's initial. Built by [avatarFallbackFor]; rendered by [NubecitaAvatar]
 * when [NubecitaAvatar]'s model is null.
 */
@Immutable
data class AvatarFallback(
    val hue: Int,
    val initial: Char?,
)

/**
 * Build the [AvatarFallback] for a person — hue from did+handle, initial from
 * the first letter-or-digit of [displayName] (falling back to [handle] when
 * the display name is null/blank), uppercased. The primitive overload is the
 * single owner of this logic; every avatar surface (AuthorUi or ActorUi) routes
 * through it instead of re-deriving the hue/initial inline.
 */
fun avatarFallbackFor(
    did: String,
    handle: String,
    displayName: String?,
): AvatarFallback =
    AvatarFallback(
        hue = avatarHueFor(did, handle),
        initial =
            (displayName?.takeIf { it.isNotBlank() } ?: handle)
                .firstOrNull { it.isLetterOrDigit() }
                ?.uppercaseChar(),
    )

/** [AuthorUi] convenience overload — delegates to the primitive [avatarFallbackFor]. */
fun avatarFallbackFor(member: AuthorUi): AvatarFallback = avatarFallbackFor(did = member.did, handle = member.handle, displayName = member.displayName)

/**
 * Circular avatar primitive. Renders [model] (anything Coil resolves) cropped to
 * a circle. When [model] is null and [fallback] is non-null, renders the hue
 * disc + initial instead of the flat placeholder — the single owner of the
 * avatarless fallback used across chats, profile, settings, and AvatarGroup.
 */
@Composable
fun NubecitaAvatar(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_AVATAR_SIZE,
    fallback: AvatarFallback? = null,
) {
    if (model == null && fallback != null) {
        val hueColor = Color.hsv(fallback.hue.toFloat(), saturation = 0.5f, value = 0.55f)
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(hueColor),
            contentAlignment = Alignment.Center,
        ) {
            fallback.initial?.let { initial ->
                Text(
                    text = initial.toString(),
                    color = if (hueColor.luminance() > 0.5f) Color.Black else Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    } else {
        NubecitaAsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

// internal so AvatarGroup (same package) can reuse the default
internal val DEFAULT_AVATAR_SIZE = 40.dp

@Preview(name = "Avatar — placeholder", showBackground = true)
@Composable
private fun NubecitaAvatarPreview() {
    NubecitaTheme {
        NubecitaAvatar(model = null, contentDescription = null)
    }
}

@Preview(name = "Avatar — fallback", showBackground = true)
@Composable
private fun NubecitaAvatarFallbackPreview() {
    NubecitaTheme {
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 47, initial = 'A'))
    }
}
