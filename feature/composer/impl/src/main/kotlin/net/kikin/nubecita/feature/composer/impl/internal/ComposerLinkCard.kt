package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.component.PostCardExternalEmbed
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R
import net.kikin.nubecita.feature.composer.impl.state.ExternalLinkStatus

/**
 * The composer's external link-preview card. Renders nothing while
 * [ExternalLinkStatus.Idle]; a compact loading row while [ExternalLinkStatus.Loading];
 * and the full preview (reusing the feed's [PostCardExternalEmbed] visual, tap
 * disabled) while [ExternalLinkStatus.Loaded]. Both non-idle states overlay a
 * dismiss `✕` that fires [onRemove].
 */
@Composable
internal fun ComposerLinkCard(
    status: ExternalLinkStatus,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        ExternalLinkStatus.Idle -> Unit
        is ExternalLinkStatus.Loading ->
            LinkCardFrame(onRemove = onRemove, modifier = modifier) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NubecitaWavyProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = displayDomain(status.url),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

        is ExternalLinkStatus.Loaded ->
            LinkCardFrame(onRemove = onRemove, modifier = modifier) {
                val preview = status.preview
                PostCardExternalEmbed(
                    uri = preview.uri,
                    domain = displayDomain(preview.uri),
                    title = preview.title,
                    description = preview.description,
                    thumbUrl = preview.imageUrl,
                    // Tap is disabled in the composer — the card is a preview of
                    // what will be posted, not a navigation target.
                    onTap = null,
                )
            }
    }
}

/** A [Box] hosting [content] with a dismiss `✕` overlaid in the top-end corner. */
@Composable
private fun LinkCardFrame(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // [content] paints its own surface (the loading row's Surface, or the
        // Loaded state's PostCardExternalEmbed); this Box is transparent and only
        // positions the dismiss ✕ over the top-end corner.
        content()
        IconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
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
                contentDescription = stringResource(R.string.composer_remove_link_action),
                filled = true,
                opticalSize = 18.dp,
            )
        }
    }
}

/** `https://www.example.com/x` → `example.com`; falls back to the raw uri. */
private fun displayDomain(uri: String): String =
    runCatching { java.net.URI(uri).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?: uri
