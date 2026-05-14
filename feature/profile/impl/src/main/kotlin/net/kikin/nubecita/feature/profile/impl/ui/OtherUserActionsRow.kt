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
 * The tap dispatches [onFollow] in both cases. The ViewModel handles
 * the optimistic `Following ↔ NotFollowing` flip + the actual
 * `app.bsky.graph.follow` create / delete write. While the write is
 * in flight (`viewerRelationship.isPending`), the Follow button is
 * disabled — the optimistic flip has already happened so the user
 * sees the future label, and the disable absorbs double-taps. The
 * ViewModel's single-flight guard is the belt-and-suspenders backup.
 *
 * [onMessage] routes to `ProfileEvent.MessageTapped`, which the VM
 * turns into a `NavigateToMessage(otherUserDid)` effect; the host
 * Composable switches to the Chats tab and pushes the per-conversation
 * thread (see `ProfileNavigationModule` for the cross-tab wiring).
 * The Message button itself is hidden when [canMessage] is `false`
 * — derived in the mapper from `associated.chat.allowIncoming` +
 * `viewer.followedBy`. When hidden, Follow expands to fill the slot.
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
    canMessage: Boolean,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onOverflowAction: (StubbedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val followLabel =
        when (viewerRelationship) {
            is ViewerRelationship.Following -> stringResource(R.string.profile_action_following)
            else -> stringResource(R.string.profile_action_follow)
        }
    val blockLabel = stringResource(R.string.profile_action_block)
    val muteLabel = stringResource(R.string.profile_action_mute)
    val reportLabel = stringResource(R.string.profile_action_report)

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val followEnabled = !viewerRelationship.isPending
        if (viewerRelationship is ViewerRelationship.Following) {
            OutlinedButton(onClick = onFollow, enabled = followEnabled, modifier = Modifier.weight(1f)) {
                Text(text = followLabel)
            }
        } else {
            Button(onClick = onFollow, enabled = followEnabled, modifier = Modifier.weight(1f)) {
                Text(text = followLabel)
            }
        }
        if (canMessage) {
            OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.profile_action_message))
            }
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
