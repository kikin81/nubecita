package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Tap target that opens the KLIPY GIF/sticker picker. Disabled while photos are
 * attached — a GIF and photos are mutually exclusive (they share the single
 * embed slot). No leading icon: the design system has no GIF glyph, and a "GIF"
 * text chip is unambiguous.
 */
@Composable
internal fun ComposerGifChip(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = { Text(stringResource(R.string.composer_gif_chip)) },
    )
}
