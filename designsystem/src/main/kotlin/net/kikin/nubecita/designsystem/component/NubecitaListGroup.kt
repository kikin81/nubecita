package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * A connected-card grouped list — the Google Play settings-sheet look.
 *
 * [items] render as **flush rows inside one rounded [surfaceContainer] card**,
 * separated by hairline dividers, with only the group's **outer** corners
 * rounded. The card owns the rounding (a single clipped [Surface]), so callers
 * never compute first / middle / last shapes — they supply the items and a
 * per-item [row] slot and the grouping is handled here.
 *
 * This replaces the hand-rolled `ListItemDefaults.segmentedShapes(index, count)`
 * pattern that was duplicated per call site (and read as separate pills because
 * of the inter-row gap). An optional [label] renders a section caption above the
 * card (M3 `labelMedium` / `onSurfaceVariant`). Empty [items] renders nothing —
 * caption included — so a not-yet-populated group leaves no stray label.
 *
 * Rows are typically [NubecitaListItem] (below), which is a plain M3 [ListItem]
 * with a transparent container so this card's tone shows through.
 *
 * @param items the group's rows, in display order.
 * @param label optional section caption shown above the card.
 * @param row renders one item's row content.
 */
@Composable
fun <T> NubecitaListGroup(
    items: ImmutableList<T>,
    modifier: Modifier = Modifier,
    label: String? = null,
    row: @Composable (T) -> Unit,
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
        Surface(
            shape = RoundedCornerShape(GROUP_CORNER_RADIUS),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        // Hairline between adjacent rows, inset past the leading
                        // slot so it aligns under the row text (Play-style).
                        HorizontalDivider(
                            modifier = Modifier.padding(start = DIVIDER_START_INSET),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    row(item)
                }
            }
        }
    }
}

/**
 * The standard row for [NubecitaListGroup]: a plain M3 [ListItem] with a
 * **transparent** container (the group card owns the surface tone) and an
 * optional [onClick]. Keeps every grouped-list row visually identical without
 * each call site re-specifying the transparent color / clickable wiring.
 */
@Composable
fun NubecitaListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = headlineContent,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// Outer corner radius of the group card. Matches the app's item-card rounding.
private val GROUP_CORNER_RADIUS = 20.dp

// Divider inset: 16dp card padding + 24dp icon + 16dp gap ≈ text start, so the
// hairline sits under the row text for rows that carry a leading icon.
private val DIVIDER_START_INSET = 56.dp

@Preview(name = "NubecitaListGroup — labeled group", showBackground = true)
@Composable
private fun NubecitaListGroupPreview() {
    NubecitaTheme {
        NubecitaListGroup(
            items = persistentListOf("First item", "Second item", "Third item"),
            label = "Section",
        ) { text ->
            NubecitaListItem(
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
        NubecitaListGroup(items = persistentListOf("Only row")) { text ->
            NubecitaListItem(headlineContent = { Text(text) }, onClick = {})
        }
    }
}
