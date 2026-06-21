package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.FollowState
import net.kikin.nubecita.feature.chats.impl.GroupMemberUi
import net.kikin.nubecita.feature.chats.impl.GroupRole
import net.kikin.nubecita.feature.chats.impl.R

/**
 * A single group-roster row: avatar, name/handle (+ optional "Added by …" and an
 * Admin badge for owners), and a trailing follow affordance. The whole row is
 * tappable ([onClick] → open the member's profile); the follow button handles its
 * own tap ([onToggleFollow]) and is hidden for the viewer's own row.
 *
 * When the viewer is the group owner ([viewerRole] == [GroupRole.Owner]), a
 * trailing ⋮ overflow menu offers [onRemove] for any non-owner, non-viewer
 * member.
 */
@Composable
internal fun GroupMemberRow(
    member: GroupMemberUi,
    viewerRole: GroupRole?,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaAvatar(
            model = member.avatarUrl,
            contentDescription = null,
            fallback =
                avatarFallbackFor(
                    did = member.did,
                    handle = member.handle,
                    displayName = member.displayName,
                ),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName ?: member.handle,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (member.role == GroupRole.Owner) {
                    // A read-only role badge — a small secondary-container pill, NOT a
                    // disabled AssistChip (which renders at 38% opacity, washing out the
                    // "Admin" label it's meant to highlight).
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.group_details_role_owner),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.group_details_handle, member.handle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            member.addedByName?.let { addedBy ->
                Text(
                    text = stringResource(R.string.group_details_added_by, addedBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!member.isViewer) {
            FollowAffordance(state = member.followState, onToggleFollow = onToggleFollow)
        }
        if (viewerRole == GroupRole.Owner && member.role == GroupRole.Member && !member.isViewer) {
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded = true }) {
                NubecitaIcon(
                    name = NubecitaIconName.MoreVert,
                    contentDescription = stringResource(R.string.group_details_member_options),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_details_remove_member)) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun FollowAffordance(
    state: FollowState,
    onToggleFollow: () -> Unit,
) {
    when (state) {
        FollowState.NotFollowing ->
            Button(onClick = onToggleFollow) {
                Text(stringResource(R.string.group_details_follow))
            }
        FollowState.Following ->
            OutlinedButton(onClick = onToggleFollow) {
                Text(stringResource(R.string.group_details_following))
            }
        FollowState.InFlight ->
            // Disabled while the optimistic follow/unfollow round-trips; keeps the
            // button footprint (and the row's min touch target) stable.
            Button(onClick = {}, enabled = false) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    // Match the disabled button's content color instead of the default
                    // vibrant primary, which clashes with the greyed-out button surface.
                    color = LocalContentColor.current,
                )
            }
    }
}
