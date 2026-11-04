package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.feed.impl.R

/**
 * The seam: where a catch-up could not bridge the whole gap between the posts
 * that arrived while the reader was away and the timeline they had already
 * read.
 *
 * It is a projection row written by the cache, never inferred from a gap in
 * positions — a reader who scrolls past a muted author leaves a gap too, and
 * drawing a break there would be a lie. Rendering it is also what keeps the
 * break from being *bare*: a timeline that silently jumps a day is worse than
 * one that says so and offers to fill it in.
 *
 * Two shapes, by [exhausted]:
 *  - **Fillable** — the range is still reachable, so the row fills itself when
 *    the reader comes near it and otherwise offers the fill as a tap.
 *  - **Exhausted** — the gap is wider than the feed keeps (D5), so no amount
 *    of filling closes it. The affordance becomes jump-to-newest, routed
 *    through the destructive refresh, which is the only honest way to the
 *    other side.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedSeamRow(
    exhausted: Boolean,
    isFilling: Boolean,
    onFill: () -> Unit,
    onJumpToNewest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill by rememberUpdatedState(onFill)
    if (!exhausted) {
        // Proximity *is* composition. A `LazyColumn` composes ahead of the
        // viewport, so the row entering the composition is the reader coming
        // within the threshold — and the effect restarts only when the row
        // leaves and re-enters, which is exactly one fill per crossing. A
        // scroll-position threshold of our own would be a second, racing
        // definition of the same distance.
        LaunchedEffect(Unit) { fill() }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(
            // Recessed, per the surface-token roles: the seam is a hole in the
            // timeline, not a card sitting on it.
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SEAM_GAP),
                modifier = Modifier.padding(horizontal = SEAM_PADDING_HORIZONTAL, vertical = SEAM_PADDING_VERTICAL),
            ) {
                when {
                    exhausted -> {
                        Text(
                            text = stringResource(R.string.feed_seam_capped),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onJumpToNewest) {
                            Text(stringResource(R.string.feed_seam_jump_to_newest))
                        }
                    }
                    isFilling -> {
                        ContainedLoadingIndicator(modifier = Modifier.size(SEAM_INDICATOR_SIZE))
                        Text(
                            text = stringResource(R.string.feed_seam_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else ->
                        TextButton(onClick = onFill) {
                            Text(stringResource(R.string.feed_seam_load))
                        }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val SEAM_GAP = 8.dp
private val SEAM_PADDING_HORIZONTAL = 16.dp
private val SEAM_PADDING_VERTICAL = 8.dp
private val SEAM_INDICATOR_SIZE = 24.dp
