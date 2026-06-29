package net.kikin.nubecita.feature.feed.impl

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Screenshot baselines for [FeedViewScreen]'s render-state matrix:
 *
 *  - Top-bar variants: pinned icon (filled bookmark) vs. unpinned (outlined bookmark)
 *  - Content branches: empty, initial-loading, initial-error variants, loaded
 *  - Each in light + dark
 *
 * Driven through [FeedViewScreenContent] directly — no Hilt graph, no live
 * network, no animation — so baselines are deterministic across machines.
 */
@PreviewTest
@Preview(name = "loaded-pinned-light", showBackground = true)
@Preview(name = "loaded-pinned-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenLoadedPinnedScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedViewScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedViewItems(count = 3),
                    isAppending = false,
                    isRefreshing = false,
                ),
            isPinned = true,
        )
    }
}

@PreviewTest
@Preview(name = "loaded-unpinned-light", showBackground = true)
@Preview(name = "loaded-unpinned-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenLoadedUnpinnedScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedViewScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedViewItems(count = 3),
                    isAppending = false,
                    isRefreshing = false,
                ),
            isPinned = false,
        )
    }
}

@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedViewScreenshotHost(
            viewState = FeedScreenViewState.Empty,
            isPinned = false,
        )
    }
}

@PreviewTest
@Preview(name = "initial-loading-light", showBackground = true)
@Preview(name = "initial-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenInitialLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedViewScreenshotHost(
            viewState = FeedScreenViewState.InitialLoading,
            isPinned = false,
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-network-light", showBackground = true)
@Preview(name = "initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenInitialErrorNetworkScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedViewScreenshotHost(
            viewState = FeedScreenViewState.InitialError(FeedError.Network),
            isPinned = false,
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-unauth-light", showBackground = true)
@Preview(name = "initial-error-unauth-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenInitialErrorUnauthScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedViewScreenshotHost(
            viewState = FeedScreenViewState.InitialError(FeedError.Unauthenticated),
            isPinned = false,
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-unknown-light", showBackground = true)
@Preview(name = "initial-error-unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedViewScreenInitialErrorUnknownScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedViewScreenshotHost(
            viewState = FeedScreenViewState.InitialError(FeedError.Unknown(cause = null)),
            isPinned = false,
        )
    }
}

@Composable
private fun FeedViewScreenshotHost(
    viewState: FeedScreenViewState,
    isPinned: Boolean,
    displayName: String = "Tech News",
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalClock provides FeedViewFixtureClock) {
        FeedViewScreenContent(
            viewState = viewState,
            listState = listState,
            snackbarHostState = snackbarHostState,
            callbacks = PostCallbacks.None,
            onRefresh = {},
            onRetry = {},
            onLoadMore = {},
            displayName = displayName,
            isPinned = isPinned,
            onPinToggle = {},
            onBack = {},
        )
    }
}

private val FEED_VIEW_FIXTURE_NOW = Instant.parse("2026-04-26T12:00:00Z")
private val FEED_VIEW_FIXTURE_CREATED_AT = Instant.parse("2026-04-26T10:00:00Z")

private object FeedViewFixtureClock : Clock {
    override fun now(): Instant = FEED_VIEW_FIXTURE_NOW
}

private fun feedViewFixturePost(
    id: String,
    text: String = "Fixture post $id — sample content for the FeedView screenshot matrix.",
): PostUi =
    PostUi(
        id = "feedview-post-$id",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:feedview-fixture-$id",
                handle = "feedviewfixture$id.bsky.social",
                displayName = "Fixture $id",
                avatarUrl = null,
            ),
        createdAt = FEED_VIEW_FIXTURE_CREATED_AT,
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 12),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

private fun fixtureFeedViewItems(count: Int): ImmutableList<FeedItemUi> =
    (1..count)
        .map { id -> FeedItemUi.Single(post = feedViewFixturePost(id.toString())) }
        .toImmutableList()
