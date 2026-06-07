package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.component.NubecitaAvatar

/**
 * Shared "full-post" presentation for the composer's reply and quote context
 * cards (nubecita-8g28.7): a leading author avatar, a `displayName @handle`
 * header row, a multi-line body preview, and an optional trailing media
 * thumbnail. Mirrors how the official client renders the reply parent / quoted
 * post inside the composer, replacing the earlier minimal two-line preview.
 *
 * Read-only — no interaction affordances (the quote card's dismiss button is the
 * caller's responsibility, placed alongside this body).
 */
@Composable
internal fun ComposerContextPostBody(
    avatarUrl: String?,
    displayName: String,
    handle: String,
    text: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NubecitaAvatar(model = avatarUrl, contentDescription = null, size = AVATAR_SIZE)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f, fill = false),
                )
                Text(
                    text = "@$handle",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Image-only posts have empty text — skip the Text so it doesn't add a
            // blank line of vertical space.
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (thumbnailUrl != null) {
            NubecitaAsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(THUMBNAIL_SIZE)
                        .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

private val AVATAR_SIZE = 36.dp
private val THUMBNAIL_SIZE = 48.dp
