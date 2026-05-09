package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontal row hosting the composer's authoring chips — currently
 * just the language chip. Reserves the layout slot so future composer
 * chips (visibility / threadgate, drafts, etc.) can land without
 * adding a new vertical row to the composer's chrome stack.
 *
 * Sits between `ComposerScreen`'s text-field surface and the
 * attachment row. When `nubecita-86m`'s
 * `HorizontalFloatingToolbar` lands, this row migrates wholesale into
 * the toolbar's content slot.
 */
@Composable
internal fun ComposerOptionsChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
