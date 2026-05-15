package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items = items, key = { it }) { query ->
            InputChip(
                selected = false,
                onClick = { onChipTap(query) },
                label = { Text(query) },
                trailingIcon = {
                    IconButton(onClick = { onChipRemove(query) }) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.search_recent_remove_content_desc),
                        )
                    }
                },
            )
        }
        item(key = "overflow") {
            IconButton(onClick = { overflowExpanded = true }) {
                NubecitaIcon(
                    name = NubecitaIconName.MoreVert,
                    contentDescription = stringResource(R.string.search_recent_overflow_content_desc),
                )
            }
            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_recent_clear_all)) },
                    onClick = {
                        overflowExpanded = false
                        onClearAll()
                    },
                )
            }
        }
    }
}
