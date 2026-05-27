package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationItemUiFixtures
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Screenshot baselines for the per-row treatments under
 * [net.kikin.nubecita.feature.notifications.impl.ui.NotificationRow].
 *
 * Covers: every reason × Single/Aggregated (where applicable) ×
 * read/unread × subjectPost null/non-null. The set is curated to the
 * most-representative combinations rather than the full cartesian
 * product (~30 baselines) — additions land here when a regression
 * shows a gap.
 */

private val PREVIEW_NOW = Instant.parse("2026-05-26T12:00:00Z")
private val PREVIEW_INDEXED_AT = Instant.parse("2026-05-26T10:00:00Z")

private object PreviewClock : Clock {
    override fun now(): Instant = PREVIEW_NOW
}

@Composable
private fun RowHost(item: NotificationItemUi) {
    CompositionLocalProvider(LocalClock provides PreviewClock) {
        NotificationRow(item = item, onClick = {}, onAvatarStackClick = {})
    }
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-like-unread-light", showBackground = true)
@Preview(name = "single-like-unread-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationRowSingleLikeUnreadScreenshot() {
    RowHost(NotificationItemUiFixtures.singleLike(isRead = false, indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-like-read-light", showBackground = true)
@Preview(name = "single-like-read-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationRowSingleLikeReadScreenshot() {
    RowHost(NotificationItemUiFixtures.singleLike(isRead = true, indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "aggregated-likes-3-light", showBackground = true)
@Preview(name = "aggregated-likes-3-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationRowAggregatedLikesThreeScreenshot() {
    RowHost(NotificationItemUiFixtures.aggregatedLikes(actorCount = 3, indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "aggregated-likes-8-light", showBackground = true)
@Preview(name = "aggregated-likes-8-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationRowAggregatedLikesEightScreenshot() {
    RowHost(NotificationItemUiFixtures.aggregatedLikes(actorCount = 8, indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-repost-light", showBackground = true)
@Composable
private fun NotificationRowSingleRepostScreenshot() {
    RowHost(NotificationItemUiFixtures.singleRepost(indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "aggregated-reposts-4-light", showBackground = true)
@Composable
private fun NotificationRowAggregatedRepostsScreenshot() {
    RowHost(NotificationItemUiFixtures.aggregatedReposts(actorCount = 4, indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-follow-light", showBackground = true)
@Preview(name = "single-follow-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationRowSingleFollowScreenshot() {
    RowHost(NotificationItemUiFixtures.singleFollow(indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "aggregated-follows-5-light", showBackground = true)
@Composable
private fun NotificationRowAggregatedFollowsScreenshot() {
    RowHost(NotificationItemUiFixtures.aggregatedFollows(actorCount = 5, indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-reply-light", showBackground = true)
@Composable
private fun NotificationRowSingleReplyScreenshot() {
    RowHost(NotificationItemUiFixtures.singleReply(indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-mention-light", showBackground = true)
@Composable
private fun NotificationRowSingleMentionScreenshot() {
    RowHost(NotificationItemUiFixtures.singleMention(indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-quote-light", showBackground = true)
@Composable
private fun NotificationRowSingleQuoteScreenshot() {
    RowHost(NotificationItemUiFixtures.singleQuote(indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-verified-light", showBackground = true)
@Composable
private fun NotificationRowSingleVerifiedScreenshot() {
    RowHost(NotificationItemUiFixtures.singleVerified(indexedAt = PREVIEW_INDEXED_AT))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "single-starterpack-light", showBackground = true)
@Composable
private fun NotificationRowSingleStarterpackScreenshot() {
    RowHost(NotificationItemUiFixtures.singleStarterpackJoined(indexedAt = PREVIEW_INDEXED_AT))
}
