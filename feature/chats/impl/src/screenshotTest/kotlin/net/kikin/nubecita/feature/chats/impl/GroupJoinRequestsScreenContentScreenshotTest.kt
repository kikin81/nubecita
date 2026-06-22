package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import kotlin.time.Instant

/**
 * Screenshot baselines for [GroupJoinRequestsScreenContent] — the group join-requests
 * review surface. The baselines exercise the loaded list, the in-flight (approving) row,
 * the settled-empty state, and the wide (tablet) constrained body.
 *
 * Paging is driven synchronously from [PagingData.from] so `collectAsLazyPagingItems`
 * presents the loaded list (NotLoading) in the screenshot host without a recomposer.
 */
private fun joinRequest(
    did: String,
    displayName: String?,
): JoinRequestUi =
    JoinRequestUi(
        did = did,
        handle = "$did.bsky.social",
        displayName = displayName,
        avatarUrl = null,
        requestedAt = Instant.parse("2026-06-22T10:00:00Z"),
    )

private val FIXTURE_REQUESTS =
    listOf(
        joinRequest("did:a", "Alice"),
        joinRequest("did:b", null),
        joinRequest("did:c", "Carol"),
    )

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "join-requests-list-light", showBackground = true, heightDp = 720)
@Preview(name = "join-requests-list-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinRequestsList() {
    val lazyItems = flowOf(PagingData.from(FIXTURE_REQUESTS)).collectAsLazyPagingItems()
    NubecitaCanvasPreviewTheme {
        GroupJoinRequestsScreenContent(
            lazyItems = lazyItems,
            inFlightDids = persistentSetOf(),
            onApprove = {},
            onReject = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "join-requests-in-flight-light", showBackground = true, heightDp = 720)
@Preview(name = "join-requests-in-flight-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinRequestsInFlight() {
    val lazyItems = flowOf(PagingData.from(FIXTURE_REQUESTS)).collectAsLazyPagingItems()
    NubecitaCanvasPreviewTheme {
        GroupJoinRequestsScreenContent(
            lazyItems = lazyItems,
            inFlightDids = persistentSetOf("did:a"),
            onApprove = {},
            onReject = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "join-requests-empty-light", showBackground = true, heightDp = 720)
@Preview(name = "join-requests-empty-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinRequestsEmpty() {
    val lazyItems = flowOf(PagingData.from(emptyList<JoinRequestUi>())).collectAsLazyPagingItems()
    NubecitaCanvasPreviewTheme {
        GroupJoinRequestsScreenContent(
            lazyItems = lazyItems,
            inFlightDids = persistentSetOf(),
            onApprove = {},
            onReject = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "join-requests-wide-light", showBackground = true, widthDp = 840, heightDp = 720)
@Preview(
    name = "join-requests-wide-dark",
    showBackground = true,
    widthDp = 840,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun GroupJoinRequestsWide() {
    val lazyItems = flowOf(PagingData.from(FIXTURE_REQUESTS)).collectAsLazyPagingItems()
    NubecitaCanvasPreviewTheme {
        GroupJoinRequestsScreenContent(
            lazyItems = lazyItems,
            inFlightDids = persistentSetOf(),
            onApprove = {},
            onReject = {},
            onClose = {},
        )
    }
}
