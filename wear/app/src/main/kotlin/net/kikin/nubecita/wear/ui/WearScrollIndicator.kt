package net.kikin.nubecita.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.ScrollIndicator

/**
 * The `ScreenScaffold` scroll indicator, hidden during a scroll capture (a
 * long screenshot) so it isn't repeated on every stitched tile.
 */
@Composable
fun WearScrollIndicator(
    state: TransformingLazyColumnState,
    modifier: Modifier = Modifier,
) {
    if (!LocalScrollCaptureInProgress.current) ScrollIndicator(state = state, modifier = modifier)
}
