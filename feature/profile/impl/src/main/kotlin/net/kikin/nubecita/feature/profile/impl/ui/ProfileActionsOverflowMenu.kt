package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R

/**
 * One entry in a [ProfileActionsOverflowMenu]. `label` is the
 * already-resolved string (call sites read via `stringResource(...)`);
 * `onClick` fires when the menu item is selected — the menu closes
 * itself first, then dispatches.
 */
internal data class OverflowEntry(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * MoreVert IconButton that opens a [DropdownMenu] with the given
 * [entries]. Owns its own `expanded` state — parents pass callbacks,
 * not visibility.
 *
 * Bead F uses this for both variants of the profile actions row:
 * own-profile (one entry → Settings), other-user (three entries →
 * Block / Mute / Report). The `contentDescription` is fixed
 * ("More options") since both variants share the affordance.
 */
@Composable
internal fun ProfileActionsOverflowMenu(
    entries: List<OverflowEntry>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.profile_action_overflow),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        expanded = false
                        entry.onClick()
                    },
                )
            }
        }
    }
}
