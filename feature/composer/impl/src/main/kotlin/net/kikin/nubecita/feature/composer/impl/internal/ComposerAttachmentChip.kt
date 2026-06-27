package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Single attachment chip rendered in the composer's chip strip. Shows
 * a square Coil-loaded thumbnail (via the brand [NubecitaAsyncImage]
 * wrapper) with a small "remove" close button overlaid in the top-
 * right corner. The remove button is gated off while the composer is
 * submitting so a stale tap can't mutate the attachment list while
 * the upload pipeline reads it.
 *
 * Sizing is pinned at 96.dp — large enough that the thumbnail and its
 * "ALT" status badge are glanceable; the chip strip scrolls horizontally
 * so it isn't bounded by how many fit on a Compact-width phone. The photo
 * uses a 12.dp rounded clip; the remove button sits on the unclipped outer
 * box so its disc isn't sliced by the corner.
 *
 * The remove-button's a11y `contentDescription` is the localized
 * `composer_remove_attachment_action`. The thumbnail itself sets a
 * `null` content description because the chip's purpose is conveyed
 * by the remove affordance and the surrounding chip strip — TalkBack
 * announcing each thumbnail individually with a generic label
 * ("image") would clutter the screen reader.
 */
@Composable
internal fun ComposerAttachmentChip(
    attachment: ComposerAttachment,
    enabled: Boolean,
    onRemoveClick: () -> Unit,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.composer_edit_description_action)
    // Outer box is NOT clipped, so the remove button can sit flush in the
    // top-right corner without its disc being sliced off by the rounded corner
    // (the bug on the 72.dp chip). Only the photo + badge get the rounded clip.
    Box(modifier = modifier.size(CHIP_SIZE)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClickLabel = editLabel, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            NubecitaAsyncImage(
                model = attachment.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            // "ALT" status badge (bottom-start): filled when the photo is described,
            // outlined when it still needs alt text. Tapping the chip opens the editor.
            AltBadge(
                described = attachment.alt.isNotBlank(),
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
            )
        }
        // Remove button — a child of the unclipped outer box so its opaque
        // `surfaceContainerHighest` disc renders whole at the corner. (Opaque,
        // not translucent: a pure icon would be invisible against a bright photo.)
        IconButton(
            onClick = onRemoveClick,
            enabled = enabled,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
            colors =
                IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Close,
                contentDescription = stringResource(R.string.composer_remove_attachment_action),
                filled = true,
                opticalSize = 18.dp,
            )
        }
    }
}

/**
 * Small "ALT" pill overlaid on a chip. [described] = filled (primary container)
 * once the photo has alt text; otherwise outlined on a translucent scrim so it
 * reads as a prompt to add one. Decorative — the chip's click action carries
 * the accessible "edit description" label, so the badge text is not announced.
 */
@Composable
private fun AltBadge(
    described: Boolean,
    modifier: Modifier = Modifier,
) {
    // Opaque containers (not a scrim/translucent fill) so the "ALT" text stays
    // legible over any photo and in both themes: described = primary, otherwise
    // the neutral surfaceContainerHighest disc the remove button also uses.
    val containerColor =
        if (described) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor =
        if (described) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(containerColor)
                .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "ALT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

private val CHIP_SIZE = 96.dp
