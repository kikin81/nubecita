package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
 * Set [singleSelect] when the rows are single-select (i.e. [row] emits
 * [NubecitaListItem]s with `onSelect`). It applies `Modifier.selectableGroup()`,
 * which is what makes a screen reader announce the rows as one group ("1 of 3")
 * instead of N unrelated radio buttons. The group cannot infer this — [row] is
 * caller-supplied, so the group never sees which mode the rows chose.
 *
 * @param items the group's rows, in display order.
 * @param label optional section caption shown above the group.
 * @param singleSelect true when [row] emits single-select rows; adds the radio
 *   group semantics.
 * @param row renders one item's row given its segment [ListItemShapes] (forward it
 *   into [NubecitaListItem]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> NubecitaListGroup(
    items: ImmutableList<T>,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleSelect: Boolean = false,
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
        Column(
            // selectableGroup() goes on the row container, not the outer Column,
            // so the optional caption stays outside the group's semantics.
            modifier = if (singleSelect) Modifier.selectableGroup() else Modifier,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
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
 * Row modes, in precedence order:
 * - A non-null [onCheckedChange] renders a **toggleable** segment via
 *   `Modifier.toggleable(role = Role.Switch)` — deliberately NOT M3's
 *   `checked`/`onCheckedChange` overload, which paints the whole row with a
 *   `selected` tint. The whole row carries the toggle state in semantics so a
 *   screen reader announces on/off, and any [trailingContent] switch should be
 *   **display-only** (`onCheckedChange = null`) so there is a single
 *   interactive node, not two.
 * - A non-null [onSelect] renders a **single-select** segment via
 *   `Modifier.selectable(role = Role.RadioButton)`, for one-of-N choices. Same
 *   ownership rule: the row owns the gesture and the state, so the
 *   [leadingContent] radio must be **display-only** (`onClick = null`). Wrap the
 *   group in [NubecitaListGroup] with `singleSelect = true` so the rows announce
 *   as one group rather than N loose radio buttons.
 *
 *   Note [onSelect] takes no argument and fires on **every** tap, including on
 *   the already-selected row — unlike a toggle, re-selecting is not a state
 *   change. If acting on it is expensive (a network write, say), guard on the
 *   current value in the ViewModel; a UI-side guard is easy to lose when the
 *   control is swapped.
 * - Otherwise a non-null [onClick] renders the interactive (button-role) segment.
 * - Otherwise the non-interactive overload — announced as text, not a disabled
 *   button — for read-only rows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaListItem(
    shapes: ListItemShapes,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val colors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    when {
        onCheckedChange != null ->
            // Row-level toggle via Modifier.toggleable (NOT M3's checked overload,
            // which treats `checked` as `selected` and tints the whole row). This
            // bakes Role.Switch + the toggle state onto the one interactive node
            // and leaves the resting visual identical to a plain row; the trailing
            // Switch is display-only. Clip to the segment shape so the ripple
            // matches the rounded corners.
            SegmentedListItem(
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                modifier =
                    modifier
                        .clip(shapes.shape)
                        .toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        ),
                content = headlineContent,
            )
        onSelect != null ->
            // Single-select sibling of the toggleable branch above, and the same
            // reasoning: Modifier.selectable puts Role.RadioButton plus the
            // selected state on the ONE interactive node, so a screen reader
            // announces the label with its state and the leading radio stays
            // display-only. Clip to the segment shape so the ripple matches the
            // rounded corners.
            SegmentedListItem(
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                modifier =
                    modifier
                        .clip(shapes.shape)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = onSelect,
                        ),
                content = headlineContent,
            )
        onClick != null ->
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
        else ->
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
