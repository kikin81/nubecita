package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.R
import net.kikin.nubecita.feature.profile.impl.StubbedAction
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Other-user actions row: Follow / Following + Message + overflow.
 *
 * [viewerRelationship] drives the Follow button's emphasis:
 *
 * - `Following` → outlined `Following` (quiet, indicates already-following state)
 * - any other relationship → filled `Follow` (CTA, primary emphasis)
 *
 * The tap dispatches [onFollow] in both cases — real writes ship under
 * follow-up bd 7.3; bead F surfaces a "Coming soon" snackbar. [onMessage]
 * routes to `ProfileEvent.MessageTapped → ShowComingSoon(Message)`.
 * The overflow menu's three entries each dispatch [onOverflowAction]
 * with the corresponding [StubbedAction] variant (`Block / Mute /
 * Report`); the screen-level handler routes those to
 * `ProfileEvent.StubActionTapped(action) → ShowComingSoon(action)`.
 *
 * The Follow / Message buttons share equal width via `Modifier.weight(1f)`;
 * the overflow stays content-sized.
 */
@Composable
internal fun OtherUserActionsRow(
    viewerRelationship: ViewerRelationship,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val followLabel =
        when (viewerRelationship) {
            ViewerRelationship.Following -> stringResource(R.string.profile_action_following)
            else -> stringResource(R.string.profile_action_follow)
        }
    val blockLabel = stringResource(R.string.profile_action_block)
    val muteLabel = stringResource(R.string.profile_action_mute)
    val reportLabel = stringResource(R.string.profile_action_report)

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (viewerRelationship == ViewerRelationship.Following) {
            OutlinedButton(onClick = onFollow, modifier = Modifier.weight(1f)) {
                Text(text = followLabel)
            }
        } else {
            Button(onClick = onFollow, modifier = Modifier.weight(1f)) {
                Text(text = followLabel)
            }
        }
        OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.profile_action_message))
        }
        ProfileActionsOverflowMenu(
            entries =
                listOf(
                    OverflowEntry(label = blockLabel, onClick = { onOverflowAction(StubbedAction.Block) }),
                    OverflowEntry(label = muteLabel, onClick = { onOverflowAction(StubbedAction.Mute) }),
                    OverflowEntry(label = reportLabel, onClick = { onOverflowAction(StubbedAction.Report) }),
                ),
        )
    }
}
