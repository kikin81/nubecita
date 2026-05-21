package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R
import net.kikin.nubecita.feature.profile.impl.StubbedAction
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Sticky action buttons ("Verbs") for the profile screen.
 *
 * Uses Material 3 Expressive [ButtonGroup] to provide a unified
 * set of primary actions.
 *
 * - Own Profile: Edit (Primary)
 * - Other Profile: Follow/Following (Primary/Tonal), Message (Tonal), Overflow (Icon)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val editLabel = stringResource(R.string.profile_action_edit)
    val followLabel =
        if (viewerRelationship is ViewerRelationship.Following) {
            stringResource(R.string.profile_action_following)
        } else {
            stringResource(R.string.profile_action_follow)
        }
    val messageLabel = stringResource(R.string.profile_action_message)
    val overflowLabel = stringResource(R.string.profile_action_overflow)
    val blockLabel = stringResource(R.string.profile_action_block)
    val muteLabel = stringResource(R.string.profile_action_mute)
    val reportLabel = stringResource(R.string.profile_action_report)

    var overflowExpanded by remember { mutableStateOf(false) }

    ButtonGroup(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        overflowIndicator = {},
    ) {
        if (ownProfile) {
            toggleableItem(
                checked = false,
                onCheckedChange = { if (it) onEdit() },
                label = editLabel,
                weight = 1f,
            )
        } else {
            val isFollowing = viewerRelationship is ViewerRelationship.Following

            toggleableItem(
                checked = isFollowing,
                onCheckedChange = { if (it || isFollowing) onFollow() },
                label = followLabel,
                weight = 1f,
                enabled = !viewerRelationship.isPending,
            )

            if (canMessage) {
                toggleableItem(
                    checked = false,
                    onCheckedChange = { if (it) onMessage() },
                    label = messageLabel,
                    weight = 1f,
                )
            }

            toggleableItem(
                checked = false,
                onCheckedChange = { if (it) overflowExpanded = true },
                label = overflowLabel,
                icon = {
                    Box {
                        NubecitaIcon(
                            name = NubecitaIconName.MoreVert,
                            contentDescription = overflowLabel,
                        )
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(blockLabel) },
                                onClick = {
                                    overflowExpanded = false
                                    onOverflowAction(StubbedAction.Block)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(muteLabel) },
                                onClick = {
                                    overflowExpanded = false
                                    onOverflowAction(StubbedAction.Mute)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(reportLabel) },
                                onClick = {
                                    overflowExpanded = false
                                    onReport()
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}
