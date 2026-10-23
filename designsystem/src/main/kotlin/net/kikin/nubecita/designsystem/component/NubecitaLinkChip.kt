package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.semanticColors

private val ChipShape = RoundedCornerShape(percent = 50)
private val ChipMinHeight = 32.dp
private val ChipIconSize = 18.dp

/**
 * A tappable pill for an outbound link — the profile-links row under a
 * profile's bio, its "+N" overflow pill, and anywhere else a short
 * labelled link needs a compact affordance.
 *
 * Two variants:
 * - **standard** (`emphasized = false`): outlined in `outlineVariant` on the
 *   surrounding surface, `onSurface` content.
 * - **emphasized**: a solid `semanticColors.success` fill with `onSuccess`
 *   content — the green "support me" pill Bluesky uses for payment
 *   platforms. The success pair is AA-tuned for each scheme; `tertiary` is
 *   deliberately avoided because link emphasis isn't auxiliary decoration.
 *
 * # Accessibility
 *
 * The pill is a single `Role.Button` node: the [label] is its text, and
 * [onClickLabel] / [onLongClickLabel] describe the actions (callers should
 * put the destination host in [onClickLabel] so a custom title can't
 * disguise where the link goes). [icon] content is decorative — pass
 * composables with a null `contentDescription` so TalkBack announces the
 * label once. The visual height is 32dp; the touch target is padded out to
 * the 48dp minimum.
 */
@Composable
fun NubecitaLinkChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val containerColor: Color
    val contentColor: Color
    if (emphasized) {
        containerColor = MaterialTheme.semanticColors.success
        contentColor = MaterialTheme.semanticColors.onSuccess
    } else {
        containerColor = Color.Transparent
        contentColor = MaterialTheme.colorScheme.onSurface
    }
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = ChipMinHeight)
                .clip(ChipShape)
                .background(containerColor)
                .then(if (emphasized) Modifier else Modifier.border(1.dp, outlineColor, ChipShape))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = onClickLabel,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    onClick = onClick,
                ).padding(start = if (icon != null) 8.dp else 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (icon != null) {
                Box(modifier = Modifier.size(ChipIconSize), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A link's site icon (favicon) for [NubecitaLinkChip]'s icon slot and link
 * rows, falling back to the globe glyph when [icon] is null, still loading, or
 * fails to load. [icon] is any Coil model — a CDN URL for a saved link, or the
 * raw bytes of a freshly fetched favicon not uploaded yet. Always decorative (`contentDescription = null`) — the surrounding
 * label carries the accessible name.
 */
@Composable
fun NubecitaLinkFavicon(
    icon: Any?,
    modifier: Modifier = Modifier,
    size: Dp = ChipIconSize,
) {
    if (icon == null) {
        NubecitaIcon(name = NubecitaIconName.Public, contentDescription = null, opticalSize = size, modifier = modifier)
        return
    }
    val painter = rememberAsyncImagePainter(model = icon)
    val state by painter.state.collectAsState()
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // The globe stands in until the favicon succeeds (loading or failed).
        if (state !is AsyncImagePainter.State.Success) {
            NubecitaIcon(name = NubecitaIconName.Public, contentDescription = null, opticalSize = size)
        }
        // Always drawn: with no explicit request size, Coil resolves the size
        // from the painter's first draw, so gating the Image on Success would
        // stall the load forever. It paints nothing until the image arrives.
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    }
}
