package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

/**
 * Tombstone row rendered in place of a normal [PostCard] when the AppView
 * returns `app.bsky.feed.defs#blockedPost` — the viewer has blocked the
 * author (or is blocked by them) and the wire carries no renderable post.
 *
 * Visual contract:
 * - `surfaceContainer` background so the row reads as a passive notice,
 *   not interactive content. Distinct from [PostCard]'s `surface` so it
 *   doesn't compete with neighboring real posts for visual weight.
 * - Italic body-medium label so the row is unambiguously
 *   "this is a system notice, not the user's words" — matches the
 *   tombstone convention in social-app's web client.
 * - Optional trailing "Unblock" `TextButton` — only renders when
 *   [onUnblock] is non-null. Hosts that surface the tombstone without an
 *   actionable unblock affordance (e.g. PostDetail rendering a reply
 *   chain where the viewer is the BLOCKED party, not the blocker) pass
 *   `onUnblock = null` and the row degrades to text-only.
 * - Same horizontal padding (20dp) as PostCard so the row aligns with
 *   neighboring posts in a LazyColumn without manual modifier surgery.
 * - Footer [HorizontalDivider] matches PostCard's bottom edge so the row
 *   reads as a peer in the timeline, not a floating chip.
 *
 * Caller wiring for the unblock RPC lives behind oftc.4 (the same
 * Block + Unblock flow that renders the overflow-menu CTA). This
 * composable is shape-only — no RPC dispatch, no optimistic state.
 */
@Composable
public fun BlockedPostCard(
    onUnblock: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.tombstone_blocked_post),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onUnblock != null) {
                TextButton(onClick = onUnblock) {
                    Text(text = stringResource(R.string.tombstone_unblock))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview(name = "BlockedPostCard — with Unblock, light", showBackground = true)
@Preview(
    name = "BlockedPostCard — with Unblock, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BlockedPostCardWithUnblockPreview() {
    NubecitaTheme {
        BlockedPostCard(onUnblock = {})
    }
}

@Preview(name = "BlockedPostCard — text-only, light", showBackground = true)
@Preview(
    name = "BlockedPostCard — text-only, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BlockedPostCardTextOnlyPreview() {
    NubecitaTheme {
        BlockedPostCard(onUnblock = null)
    }
}
