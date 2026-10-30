package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.OutgoingPostStatusUi
import net.kikin.nubecita.data.models.OutgoingPostUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview
import kotlin.time.Instant

/**
 * Screenshot baselines for [OutgoingPostCard]: sending, failed (Retry / Discard)
 * and sent (an ordinary post card).
 */

@PreviewTest
@Preview(name = "sending-light", showBackground = true)
@Preview(name = "sending-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun OutgoingPostCardSendingScreenshot() {
    OutgoingPostCard(item = outgoing(OutgoingPostStatusUi.Sending), onRetry = {}, onDiscard = {})
}

@PreviewTest
@Preview(name = "failed-light", showBackground = true)
@Preview(name = "failed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun OutgoingPostCardFailedScreenshot() {
    OutgoingPostCard(item = outgoing(OutgoingPostStatusUi.Failed), onRetry = {}, onDiscard = {})
}

@PreviewTest
@Preview(name = "sent-light", showBackground = true)
@Preview(name = "sent-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun OutgoingPostCardSentScreenshot() {
    OutgoingPostCard(item = outgoing(OutgoingPostStatusUi.Sent), onRetry = {}, onDiscard = {})
}

private fun outgoing(status: OutgoingPostStatusUi) =
    OutgoingPostUi(
        localId = "local-1",
        status = status,
        post =
            PostUi(
                id = "local:local-1",
                cid = "",
                author = AuthorUi(did = "did:plc:me", handle = "nubecita.app", displayName = "Nubecita.app", avatarUrl = null),
                // Far enough in the past that PostCard renders an absolute date,
                // not a relative "4h" that drifts with the screenshot host's clock.
                createdAt = Instant.parse("2025-10-15T12:00:00Z"),
                text = "Good morning from Nubecita ☁️",
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(isOwnPost = true),
                repostedBy = null,
            ),
    )
