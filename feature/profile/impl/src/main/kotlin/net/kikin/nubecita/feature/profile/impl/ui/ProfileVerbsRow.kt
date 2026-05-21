package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
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
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R
import net.kikin.nubecita.feature.profile.impl.StubbedAction
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Sticky "Verbs" row for the profile — primary actions docked just
 * below the top bar.
 *
 * - Own profile: a single tonal "Edit profile" pill (Settings lives
 *   in the top bar).
 * - Other profile: a primary "Follow" / tonal "Following" pill, an
 *   optional tonal "Message" pill (when DM-reachable), and an
 *   overflow [IconButton] anchoring the moderation menu.
 *
 * Plain [Row] + pill [Button]s on purpose: M3 Expressive's
 * [androidx.compose.material3.ButtonGroup] is a segmented-selection
 * primitive and its `toggleableItem` carries selection state — these
 * are one-shot verbs, not toggles.
 */
@Composable
internal fun ProfileVerbsRow(
    ownProfile: Boolean,
    viewerRelationship: ViewerRelationship,
    canMessage: Boolean,
    onEdit: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onReport: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ownProfile) {
            FilledTonalButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.profile_action_edit))
            }
        } else {
            FollowVerbButton(
                viewerRelationship = viewerRelationship,
                onFollow = onFollow,
                modifier = Modifier.weight(1f),
            )
            if (canMessage) {
                FilledTonalButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.profile_action_message))
                }
            }
            ProfileOverflowMenuButton(
                onReport = onReport,
                onOverflowAction = onOverflowAction,
            )
        }
    }
}

@Composable
private fun RowScope.FollowVerbButton(
    viewerRelationship: ViewerRelationship,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFollowing = viewerRelationship is ViewerRelationship.Following
    val enabled = !viewerRelationship.isPending
    if (isFollowing) {
        FilledTonalButton(
            onClick = onFollow,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(stringResource(R.string.profile_action_following))
        }
    } else {
        Button(
            onClick = onFollow,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(stringResource(R.string.profile_action_follow))
        }
    }
}

@Composable
private fun ProfileOverflowMenuButton(
    onReport: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.profile_action_overflow),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_action_block)) },
                onClick = {
                    expanded = false
                    onOverflowAction(StubbedAction.Block)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_action_mute)) },
                onClick = {
                    expanded = false
                    onOverflowAction(StubbedAction.Mute)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_action_report)) },
                onClick = {
                    expanded = false
                    onReport()
                },
            )
        }
    }
}
