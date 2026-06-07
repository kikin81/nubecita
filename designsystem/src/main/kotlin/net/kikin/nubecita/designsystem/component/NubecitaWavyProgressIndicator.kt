package net.kikin.nubecita.designsystem.component

import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Brand indeterminate loading spinner. The single indeterminate
 * progress indicator used across the app, so every "something is loading"
 * spinner gets the same M3 Expressive wavy ring instead of each call site
 * re-importing the experimental [CircularWavyProgressIndicator] API and
 * picking its own size/color. New spinners use this, not a raw
 * `CircularProgressIndicator`.
 *
 * Matches the wavy indicator already shipped in [NubecitaPrimaryButton].
 * Size is controlled by [modifier] (the wavy ring reads well from ~16.dp up
 * — the button uses 20.dp); below that the waves don't render, so a tiny
 * inline dot should stay a plain indicator.
 *
 * Scope notes:
 * - This is the **indeterminate** spinner only — there is no `progress`
 *   parameter. Determinate progress (e.g. the composer character counter,
 *   the video scrubber) uses the determinate `*ProgressIndicator` directly.
 * - The **contained morphing** loading indicator is reserved for
 *   pull-to-refresh ([NubecitaPullToRefreshBox]); this is the free-standing
 *   spinner for content/action/overlay loads.
 *
 * @param modifier sizing/placement; defaults to the wavy indicator's natural size.
 * @param color the ring color; defaults to the M3 wavy indicator color
 *   (override e.g. `Color.White` over media, or a container's content color).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
) {
    CircularWavyProgressIndicator(
        modifier = modifier,
        color = color,
    )
}
