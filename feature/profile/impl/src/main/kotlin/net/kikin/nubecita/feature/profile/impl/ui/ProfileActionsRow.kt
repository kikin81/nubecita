package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Hero actions row.
 *
 * In Bead D only the `ownProfile = true` branch is fully wired (Edit +
 * overflow). The `ownProfile = false` branch renders the same affordances
 * defensively — Bead F replaces this with the Follow + Message + overflow
 * row. Until then, other-user navigation reaches a screen that still
 * renders the Edit row; not a primary user journey in Bead D.
 *
 * The overflow icon's onClick is a deliberate no-op in Bead D — Bead F
 * wires a real DropdownMenu containing the Settings entry. The icon's
 * contentDescription communicates the affordance for accessibility.
 *
 * `ownProfile` is accepted today so the call site at the ProfileHero
 * layer doesn't need to change shape when Bead F lights up the
 * other-user branch; the param is intentionally unread for Bead D.
 */
@Composable
internal fun ProfileActionsRow(
    @Suppress("UNUSED_PARAMETER") ownProfile: Boolean,
    onEdit: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onEdit) {
            Text(text = stringResource(R.string.profile_action_edit))
        }
        IconButton(onClick = onOverflow) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.profile_action_overflow),
            )
        }
    }
}
