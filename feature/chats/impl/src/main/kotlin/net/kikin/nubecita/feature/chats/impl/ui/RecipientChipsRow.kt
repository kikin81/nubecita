package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.RecipientUi

/**
 * A FlowRow of selected recipients as removable M3 [InputChip]s (avatar + label + ✕).
 * Shared by the add-members and new-group pickers. When [enabled] is false the chips are
 * non-interactive (used while a submit is in flight).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecipientChipsRow(
    selected: ImmutableList<RecipientUi>,
    onRemove: (did: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selected.forEach { r ->
            InputChip(
                selected = true,
                enabled = enabled,
                onClick = { onRemove(r.did) },
                label = { Text(r.displayName ?: r.handle, maxLines = 1) },
                avatar = {
                    NubecitaAvatar(
                        model = r.avatarUrl,
                        contentDescription = null,
                        size = InputChipDefaults.AvatarSize,
                        fallback =
                            avatarFallbackFor(
                                did = r.did,
                                handle = r.handle,
                                displayName = r.displayName,
                            ),
                    )
                },
                trailingIcon = {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.add_members_remove_chip),
                    )
                },
            )
        }
    }
}
