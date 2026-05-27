package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.time.rememberRelativeTimeText
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationItemUiFixtures
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NotificationReasonIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.notifications.impl.R

/**
 * One row in the Notifications LazyColumn. Branches over
 * [NotificationItemUi.Single] / [NotificationItemUi.Aggregated] for the
 * avatar-stack + chevron treatment but otherwise shares the same
 * layout shell:
 *
 * ```
 * [reasonIcon] [stacked avatars]
 *              [headline · timestamp]
 *              [subjectPostPreview (optional)]
 * ```
 *
 * Background tint is the unread tonal-tint contract: `surfaceContainerLow`
 * when `!isRead`, plain `surface` when read (see the feature-notifications
 * spec's "Unread rows render with a tonal background tint" requirement).
 *
 * Clicks dispatch through two paths:
 * - The whole row is `clickable(onClick = onClick)` — VM resolves the
 *   deep-link target by reason (see `NotificationsViewModel.deepLinkTarget`).
 * - On aggregated rows, the chevron's clickable swallows the tap and
 *   fires [onAvatarStackClick] so the host can present the actor list
 *   bottom sheet. Single rows omit the chevron entirely (per spec
 *   "Single rows do not render a chevron").
 */
@Composable
internal fun NotificationRow(
    item: NotificationItemUi,
    onClick: () -> Unit,
    onAvatarStackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (item.isRead) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NotificationReasonIcon(
            reason = item.reason,
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(REASON_ICON_SIZE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StackedAvatarRow(actors = item.actors)
                if (item is NotificationItemUi.Aggregated) {
                    Spacer(Modifier.width(2.dp))
                    NubecitaIcon(
                        name = NubecitaIconName.ExpandMore,
                        contentDescription = stringResource(R.string.notifications_row_expand_actors),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .size(CHEVRON_SIZE)
                                .clickable(onClick = onAvatarStackClick),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HeadlineWithTimestamp(item = item)
            val subject = item.subjectPost
            if (subject != null) {
                Spacer(Modifier.height(8.dp))
                NotificationSubjectPreview(
                    post = subject,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HeadlineWithTimestamp(item: NotificationItemUi) {
    val resources = LocalResources.current
    val headline = remember(item, resources) { item.buildHeadline(resources) }
    val timestamp by rememberRelativeTimeText(then = item.indexedAt)
    Text(
        text = stringResource(R.string.notifications_headline_with_time, headline, timestamp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Resolve the headline copy for [item] against [resources]. Pure
 * (`Resources` is the only Android dependency) so it can be unit-tested
 * without spinning up Compose.
 *
 * Single-actor rows pick the `*_single` String resource; aggregated
 * rows pick the matching `<plurals>` resource and pass `actors.size - 1`
 * as the count of "other" actors (pattern (a) in design.md open
 * question 3: "alice and 3 others liked your post").
 *
 * Self-shaped reasons (Verified / Unverified) ignore the actor list —
 * the copy is about the recipient. Unknown reasons fall through to a
 * generic "{actor} interacted with you" so the row still renders.
 */
internal fun NotificationItemUi.buildHeadline(resources: Resources): String {
    val lead = actors.firstOrNull()
    val leadName =
        when {
            lead == null -> ""
            lead.displayName.isNotEmpty() -> lead.displayName
            else -> lead.handle
        }
    val otherCount = (actors.size - 1).coerceAtLeast(0)
    return when (reason) {
        NotificationReason.Like, NotificationReason.LikeViaRepost ->
            if (otherCount == 0) {
                resources.getString(R.string.notifications_headline_like_single, leadName)
            } else {
                resources.getQuantityString(
                    R.plurals.notifications_headline_like,
                    otherCount,
                    leadName,
                    otherCount,
                )
            }
        NotificationReason.Repost, NotificationReason.RepostViaRepost ->
            if (otherCount == 0) {
                resources.getString(R.string.notifications_headline_repost_single, leadName)
            } else {
                resources.getQuantityString(
                    R.plurals.notifications_headline_repost,
                    otherCount,
                    leadName,
                    otherCount,
                )
            }
        NotificationReason.Follow ->
            if (otherCount == 0) {
                resources.getString(R.string.notifications_headline_follow_single, leadName)
            } else {
                resources.getQuantityString(
                    R.plurals.notifications_headline_follow,
                    otherCount,
                    leadName,
                    otherCount,
                )
            }
        NotificationReason.Reply ->
            resources.getString(R.string.notifications_headline_reply, leadName)
        NotificationReason.Quote ->
            resources.getString(R.string.notifications_headline_quote, leadName)
        NotificationReason.Mention ->
            resources.getString(R.string.notifications_headline_mention, leadName)
        NotificationReason.StarterpackJoined ->
            resources.getString(R.string.notifications_headline_starterpack_joined, leadName)
        NotificationReason.SubscribedPost ->
            resources.getString(R.string.notifications_headline_subscribed_post, leadName)
        NotificationReason.ContactMatch ->
            resources.getString(R.string.notifications_headline_contact_match, leadName)
        NotificationReason.Verified ->
            resources.getString(R.string.notifications_headline_verified)
        NotificationReason.Unverified ->
            resources.getString(R.string.notifications_headline_unverified)
        NotificationReason.Unknown ->
            resources.getString(R.string.notifications_headline_unknown, leadName)
    }
}

private val REASON_ICON_SIZE = 24.dp
private val CHEVRON_SIZE = 20.dp

// ---------- Previews -------------------------------------------------------

@Preview(name = "NotificationRow — single like (unread, light)", showBackground = true)
@Composable
private fun NotificationRowSingleLikePreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleLike(isRead = false),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — single like (read, light)", showBackground = true)
@Composable
private fun NotificationRowSingleLikeReadPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleLike(isRead = true),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — aggregated likes (3 actors)", showBackground = true)
@Composable
private fun NotificationRowAggregatedLikesPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.aggregatedLikes(actorCount = 3),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — aggregated likes (8 actors, +3)", showBackground = true)
@Composable
private fun NotificationRowAggregatedLikesOverflowPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.aggregatedLikes(actorCount = 8),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — single repost", showBackground = true)
@Composable
private fun NotificationRowSingleRepostPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleRepost(),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — aggregated reposts (4 actors)", showBackground = true)
@Composable
private fun NotificationRowAggregatedRepostsPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.aggregatedReposts(actorCount = 4),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — single follow (no subject)", showBackground = true)
@Composable
private fun NotificationRowSingleFollowPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleFollow(),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — aggregated follows (5 actors)", showBackground = true)
@Composable
private fun NotificationRowAggregatedFollowsPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.aggregatedFollows(actorCount = 5),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — single reply", showBackground = true)
@Composable
private fun NotificationRowSingleReplyPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleReply(),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — single mention", showBackground = true)
@Composable
private fun NotificationRowSingleMentionPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleMention(),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — single quote", showBackground = true)
@Composable
private fun NotificationRowSingleQuotePreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleQuote(),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(name = "NotificationRow — verified", showBackground = true)
@Composable
private fun NotificationRowVerifiedPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.singleVerified(),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}

@Preview(
    name = "NotificationRow — aggregated likes (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NotificationRowAggregatedLikesDarkPreview() {
    NubecitaTheme {
        NotificationRow(
            item = NotificationItemUiFixtures.aggregatedLikes(actorCount = 3),
            onClick = {},
            onAvatarStackClick = {},
        )
    }
}
