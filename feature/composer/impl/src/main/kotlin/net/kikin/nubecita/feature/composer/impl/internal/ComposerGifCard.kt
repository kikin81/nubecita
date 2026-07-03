package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.KlipyMediaUi
import net.kikin.nubecita.data.models.previewAspectRatio
import net.kikin.nubecita.designsystem.component.PostCardGifEmbed
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R

/**
 * The composer's picked-GIF preview — the same animated render the post will
 * show (via the feed's [PostCardGifEmbed], using the light preview rendition)
 * with a dismiss `✕` overlaid in the top-end corner, mirroring [ComposerLinkCard].
 * Rendered only while a GIF is attached.
 */
@Composable
internal fun ComposerGifCard(
    gif: KlipyMediaUi,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        PostCardGifEmbed(
            gifUrl = gif.previewUrl,
            aspectRatio = gif.previewAspectRatio,
            alt = gif.title,
        )
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
                contentDescription = stringResource(R.string.composer_remove_gif_action),
                filled = true,
                opticalSize = 18.dp,
            )
        }
    }
}
