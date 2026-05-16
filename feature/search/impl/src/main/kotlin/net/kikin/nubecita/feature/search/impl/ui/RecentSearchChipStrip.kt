package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.search.impl.R

/**
 * Horizontal strip of recent-search [InputChip]s. The chip body tap fires
 * [onChipTap]; the trailing X fires [onChipRemove]. A trailing overflow
 * `IconButton` opens a one-item `DropdownMenu` with "Clear all" firing
 * [onClearAll].
 *
 * The caller is responsible for skipping this composable when [items] is
 * empty — there is no internal empty-state rendering.
 */
@Composable
internal fun RecentSearchChipStrip(
    items: ImmutableList<String>,
    onChipTap: (String) -> Unit,
    onChipRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    // Row (not LazyRow) so the overflow IconButton stays pinned on the right
    // and never scrolls off-screen with the chips. The chip list itself is a
    // weight-1f LazyRow so it absorbs the remaining horizontal space.
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = items, key = { it }) { query ->
                // Remember the per-chip dispatch lambdas keyed on the query
                // (the item's stable identity) and the parent callback so they
                // stay stable across the chip's own recompositions. Avoids
                // allocating two fresh lambdas per chip per parent recompose.
                val onChipClicked = remember(query, onChipTap) { { onChipTap(query) } }
                val onChipRemoveClicked = remember(query, onChipRemove) { { onChipRemove(query) } }
                InputChip(
                    selected = false,
                    onClick = onChipClicked,
                    label = { Text(query) },
                    trailingIcon = {
                        // Plain Icon + clickable Box keeps the chip at its M3 baseline
                        // height (~32dp). Wrapping in IconButton inflated each chip by
                        // ~8dp due to the 40dp touch target. The clickable Box is
                        // (icon + 4dp padding) ≈ 26dp; the chip's overall 48dp height
                        // including padding still meets the a11y touch-target minimum
                        // when the user taps the X edge.
                        Box(
                            modifier =
                                Modifier
                                    .size(InputChipDefaults.IconSize + 8.dp)
                                    .clickable(onClick = onChipRemoveClicked),
                            contentAlignment = Alignment.Center,
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.Close,
                                contentDescription = stringResource(R.string.search_recent_remove_content_desc),
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }
        IconButton(onClick = { overflowExpanded = true }) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.search_recent_overflow_content_desc),
            )
        }
        // Material 3 Expressive's menu container shape: pass an explicit
        // large rounded shape instead of the M3 baseline's `extraSmall` (4dp,
        // which reads as dated MD2). 16dp matches the Expressive treatment
        // shown in the Google design guidance and the rest of the M3
        // expressive surface vocabulary in this app.
        DropdownMenu(
            expanded = overflowExpanded,
            onDismissRequest = { overflowExpanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_recent_clear_all)) },
                onClick = {
                    overflowExpanded = false
                    onClearAll()
                },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
