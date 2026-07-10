package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * A Material 3 **Expressive** connected/segmented grouped list.
 *
 * [items] render as **[SegmentedListItem] segments** — separate rounded rows with
 * the framework's 2dp [ListItemDefaults.SegmentedGap] between them and
 * position-aware corners from [ListItemDefaults.segmentedShapes]: the first row
 * has large top corners, the last row large bottom corners, middle rows small
 * corners, and a single row is fully rounded. This is the M3 Expressive list look
 * (the reference we're matching), not a flat single card.
 *
 * The group computes each row's shape and hands it to the [row] slot, so callers
 * still **never compute first / middle / last positions** — they supply the items
 * and a per-item [row] that forwards the passed `shapes` into [NubecitaListItem].
 *
 * The segment fill is [surfaceContainerHigh][androidx.compose.material3.ColorScheme.surfaceContainerHigh]
 * (a **raised** tone) so the segments read clearly against the screen's near-black
 * `surface` canvas on dark — `surfaceContainer` sits too close to the canvas and
 * the inter-segment gaps look muddy. See `docs/design-system/surface-roles.md`.
 *
 * An optional [label] renders a section caption above the group (M3 `labelMedium`
 * / `onSurfaceVariant`). Empty [items] renders nothing — caption included — so a
 * not-yet-populated group leaves no stray label.
 *
 * @param items the group's rows, in display order.
 * @param label optional section caption shown above the group.
 * @param row renders one item's row given its segment [ListItemShapes] (forward it
 *   into [NubecitaListItem]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> NubecitaListGroup(
    items: ImmutableList<T>,
    modifier: Modifier = Modifier,
    label: String? = null,
    row: @Composable (item: T, shapes: ListItemShapes) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, item ->
                row(item, ListItemDefaults.segmentedShapes(index = index, count = items.size))
            }
        }
    }
}

/**
 * The standard row for [NubecitaListGroup]: a Material 3 [SegmentedListItem] shaped
 * by the [shapes] the group passes down, filled with the raised
 * [surfaceContainerHigh][androidx.compose.material3.ColorScheme.surfaceContainerHigh]
 * segment tone.
 *
 * A non-null [onClick] renders the interactive (button-role) segment; a null
 * [onClick] renders the non-interactive overload — announced as text, not a
 * disabled button — for read-only rows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaListItem(
    shapes: ListItemShapes,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val colors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = colors,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            modifier = modifier,
            content = headlineContent,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = colors,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            modifier = modifier,
            content = headlineContent,
        )
    }
}

@Preview(name = "NubecitaListGroup — labeled group", showBackground = true)
@Composable
private fun NubecitaListGroupPreview() {
    NubecitaTheme {
        NubecitaListGroup(
            items = persistentListOf("First item", "Second item", "Third item"),
            label = "Section",
        ) { text, shapes ->
            NubecitaListItem(
                shapes = shapes,
                headlineContent = { Text(text) },
                onClick = {},
            )
        }
    }
}

@Preview(name = "NubecitaListGroup — single row", showBackground = true)
@Composable
private fun NubecitaListGroupSinglePreview() {
    NubecitaTheme {
        NubecitaListGroup(items = persistentListOf("Only row")) { text, shapes ->
            NubecitaListItem(shapes = shapes, headlineContent = { Text(text) }, onClick = {})
        }
    }
}
