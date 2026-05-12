package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Own-profile actions row: an Edit button next to the overflow menu.
 *
 * Edit dispatches [onEdit] (the screen routes it to
 * `ProfileEvent.EditTapped` → `ShowComingSoon(Edit)`). The overflow
 * menu has one entry — Settings — wired to [onSettings] (routes to
 * `ProfileEvent.SettingsTapped` → `NavigateToSettings`, the only
 * user-reachable entry point for the Settings sub-route in this epic).
 *
 * Real Edit writes ship under follow-up bd 7.4.
 */
@Composable
internal fun OwnProfileActionsRow(
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsLabel = stringResource(R.string.profile_action_settings)
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onEdit) {
            Text(text = stringResource(R.string.profile_action_edit))
        }
        ProfileActionsOverflowMenu(
            entries = listOf(OverflowEntry(label = settingsLabel, onClick = onSettings)),
        )
    }
}
