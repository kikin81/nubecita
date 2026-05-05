package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Single attachment chip rendered in the composer's chip strip. Shows
 * a square Coil-loaded thumbnail (via the brand [NubecitaAsyncImage]
 * wrapper) with a small "remove" close button overlaid in the top-
 * right corner. The remove button is gated off while the composer is
 * submitting so a stale tap can't mutate the attachment list while
 * the upload pipeline reads it.
 *
 * Sizing is pinned at 72.dp — small enough that 4 chips + the leading
 * "Add image" affordance fit on a Compact-width phone, large enough
 * that the thumbnail is glanceable. The corner radius matches the
 * design-system 8.dp surface radius used elsewhere.
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(CHIP_SIZE)
                .clip(RoundedCornerShape(8.dp)),
    ) {
        NubecitaAsyncImage(
            model = attachment.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        // Remove button. The container color paints an opaque
        // `surfaceContainerHighest` disc behind the icon so it stays
        // legible against busy thumbnails (a pure black close icon on
        // a sky-blue photo is otherwise invisible). Opaque is a
        // deliberate choice: a translucent treatment makes the icon
        // bleed against bright underlying pixels at the worst-case
        // photo content. 24.dp button at 4.dp from the chip's top-
        // right edge keeps the touch target reachable without
        // dominating the 72.dp thumbnail.
        IconButton(
            onClick = onRemoveClick,
            enabled = enabled,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
            colors =
                IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.composer_remove_attachment_action),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private val CHIP_SIZE = 72.dp
