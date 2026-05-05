package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.composer.impl.ComposerViewModel
import kotlin.math.max

/**
 * Circular character counter rendered as an M3 [CircularProgressIndicator]
 * with three tonal bands per the spec:
 *
 * - **0..239 graphemes** — progress fills in the M3 primary tone.
 * - **240..289 graphemes** — progress shifts to the tertiary/warn tone.
 * - **290..300+ graphemes** — progress shifts to the error tone, and
 *   when over-limit the inner numeric label switches to the
 *   "remaining characters" form (negative number) so the user sees
 *   exactly how much over they are.
 *
 * The numeric label inside the arc shows characters remaining (300
 * minus current count) when within the limit. When over-limit it
 * shows the negative offset (e.g. `-12`).
 *
 * Compose-perf notes:
 * - Color is animated via [animateColorAsState] so band transitions
 *   don't pop discontinuously. The animation is cheap (one
 *   ColorAnimatable per band change, not per recomposition).
 * - The progress fraction is computed inline as a primitive Float;
 *   no [androidx.compose.runtime.derivedStateOf] is needed — the
 *   composable already only recomposes when [graphemeCount] changes
 *   (the parent reads it from `state.graphemeCount` which is a
 *   primitive Int field).
 * - Accessibility: the `semantics { contentDescription = … }` block
 *   merges the count + state into a single TalkBack announcement
 *   ("12 characters remaining" / "Over the limit by 5") rather than
 *   reading the bare integer label, which would be ambiguous out of
 *   context.
 */
@Composable
internal fun ComposerCharacterCounter(
    graphemeCount: Int,
    isOverLimit: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val limit = ComposerViewModel.MAX_GRAPHEMES
    // Cap progress at 1.0 so the arc visually fills at the limit; the
    // overflow state is communicated via color + the negative numeric
    // label inside the arc, not by a > 1.0 progress fraction (which
    // CircularProgressIndicator would render as a double-loop).
    val progress = (graphemeCount.toFloat() / limit.toFloat()).coerceAtMost(1f)

    val targetColor =
        when {
            isOverLimit -> MaterialTheme.colorScheme.error
            graphemeCount >= 290 -> MaterialTheme.colorScheme.error
            graphemeCount >= 240 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
    val arcColor: Color by animateColorAsState(targetValue = targetColor, label = "composer-counter-color")

    Box(
        modifier =
            modifier
                .size(40.dp)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(40.dp),
            color = arcColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            text = labelFor(graphemeCount, limit, isOverLimit),
            color = arcColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun labelFor(
    graphemeCount: Int,
    limit: Int,
    isOverLimit: Boolean,
): String =
    if (isOverLimit) {
        // Negative — "you're over the limit by N". Glance affords
        // immediate-fix understanding.
        "-${graphemeCount - limit}"
    } else {
        // Show remaining — same convention as Twitter / X, easier to
        // glance at than the running count when approaching the limit.
        max(0, limit - graphemeCount).toString()
    }
