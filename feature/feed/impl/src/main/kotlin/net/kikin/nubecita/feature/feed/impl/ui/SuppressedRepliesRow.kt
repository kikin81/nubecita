package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.feed.impl.R
import net.kikin.nubecita.designsystem.R as DesignSystemR

/**
 * "N more replies in this thread" — the affordance for replies the feed
 * suppressed by thread-root de-duplication.
 *
 * At most one item per thread root is rendered, so several replies into one
 * thread collapse to a single card. Measured on a production account, that
 * hides ~7.5% of feed items, one thread collapsing from six cards to one.
 * Without this row the viewer gets no signal the rest exist. The official
 * client drops silently; this is the one place Nubecita deliberately does not
 * match it (`openspec/changes/fix-feed-thread-root-dedupe`, decision D6).
 *
 * Renders nothing when [count] is zero, so callers can pass the item's
 * `suppressedReplyCount` unconditionally.
 *
 * Sits INSIDE the item's `Surface` so it reads as part of that card rather
 * than as a floating row between cards. The leading indent matches the avatar
 * gutter so it lines up with the post bodies above it.
 */
@Composable
internal fun SuppressedRepliesRow(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val label = pluralStringResource(R.plurals.feed_suppressed_replies, count, count)
    // TalkBack renders onClickLabel as "double-tap to <label>", so it must be a
    // VERB phrase. Reusing ThreadFold's string keeps that grammatical ("…to view
    // full thread") and tells the user the row navigates rather than expanding
    // inline — which the row's own noun-phrase text does not say.
    val clickLabel = stringResource(DesignSystemR.string.thread_fold_view_full_thread)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // clickable before padding so the whole padded row is the
                // touch target rather than just the text bounds. role +
                // onClickLabel match ThreadFold, the sibling affordance inside
                // this same card — without them TalkBack announces the text but
                // neither identifies it as a button nor describes the action.
                .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
