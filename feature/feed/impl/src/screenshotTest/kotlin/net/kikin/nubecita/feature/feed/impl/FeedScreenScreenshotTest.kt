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
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Screenshot baselines for `FeedScreen`'s render-state matrix:
 * empty, initial-loading, three initial-error variants (Network,
 * Unauthenticated, Unknown), loaded, loaded+refreshing, loaded+appending —
 * each in light + dark. Driven through `FeedScreenContent` directly with
 * fixture `FeedScreenViewState` inputs so the captures are deterministic
 * across machines (no Hilt graph, no live network, no animation).
 */

@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(viewState = FeedScreenViewState.Empty)
    }
}

@PreviewTest
@Preview(name = "initial-loading-light", showBackground = true)
@Preview(name = "initial-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(viewState = FeedScreenViewState.InitialLoading)
    }
}

@PreviewTest
@Preview(name = "initial-error-network-light", showBackground = true)
@Preview(name = "initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialErrorNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState = FeedScreenViewState.InitialError(FeedError.Network),
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-unauth-light", showBackground = true)
@Preview(name = "initial-error-unauth-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialErrorUnauthScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState = FeedScreenViewState.InitialError(FeedError.Unauthenticated),
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-unknown-light", showBackground = true)
@Preview(name = "initial-error-unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenInitialErrorUnknownScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState = FeedScreenViewState.InitialError(FeedError.Unknown(cause = null)),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-light", showBackground = true)
@Preview(name = "loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedItems(count = 3),
                    isAppending = false,
                    isRefreshing = false,
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-refreshing-light", showBackground = true)
@Preview(name = "loaded-refreshing-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedRefreshingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedItems(count = 3),
                    isAppending = false,
                    isRefreshing = true,
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-with-cluster-light", showBackground = true)
@Preview(name = "loaded-with-cluster-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedWithClusterScreenshot() {
    // Loaded viewState with a Single + a ReplyCluster (with ellipsis) to
    // verify the cluster renders correctly within the feed and visually
    // contrasts with neighboring singles. Locks the cross-author thread
    // cluster integration end-to-end.
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedItemsWithCluster(),
                    isAppending = false,
                    isRefreshing = false,
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-appending-light", showBackground = true)
@Preview(name = "loaded-appending-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedAppendingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedItems(count = 3),
                    isAppending = true,
                    isRefreshing = false,
                ),
        )
    }
}

@Composable
private fun FeedScreenScreenshotHost(viewState: FeedScreenViewState) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Provide a fixed clock so PostCard's relative-time label is
    // deterministic — pairs with FIXTURE_CREATED_AT below to render "2h"
    // forever. No Clock.System.now() reads happen anywhere in this file.
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        FeedScreenContent(
            viewState = viewState,
            listState = listState,
            snackbarHostState = snackbarHostState,
            callbacks = PostCallbacks.None,
            onRefresh = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

private val FIXTURE_NOW = Instant.parse("2026-04-26T12:00:00Z")
private val FIXTURE_CREATED_AT = Instant.parse("2026-04-26T10:00:00Z")

private object FixtureClock : Clock {
    override fun now(): Instant = FIXTURE_NOW
}

private fun fixturePost(
    id: String,
    text: String = "Fixture post $id — sample content for the screen-level screenshot matrix.",
): PostUi =
    PostUi(
        id = "post-$id",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:fixture-$id",
                handle = "fixture$id.bsky.social",
                displayName = "Fixture $id",
                avatarUrl = null,
            ),
        createdAt = FIXTURE_CREATED_AT,
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 12),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

private fun fixtureFeedItems(count: Int): ImmutableList<FeedItemUi> =
    (1..count)
        .map { id -> FeedItemUi.Single(post = fixturePost(id.toString())) }
        .toImmutableList()

/**
 * Mixed fixture for the cluster screenshot baseline — a [FeedItemUi.Single]
 * sandwiched by a [FeedItemUi.ReplyCluster] (with ellipsis) and another
 * [FeedItemUi.Single]. Locks the visual contrast between standalone posts
 * and clusters in the screen-level capture.
 */
private fun fixtureFeedItemsWithCluster(): ImmutableList<FeedItemUi> =
    persistentListOf<FeedItemUi>(
        FeedItemUi.Single(post = fixturePost("1", text = "First standalone post in the screenshot fixture.")),
        FeedItemUi.ReplyCluster(
            root = fixturePost("root", text = "Root post that started a thread."),
            parent = fixturePost("parent", text = "Immediate parent — child of an elided post above."),
            leaf = fixturePost("leaf", text = "Leaf reply — surfaced into the timeline."),
            hasEllipsis = true,
        ),
        FeedItemUi.Single(post = fixturePost("3", text = "Trailing standalone post after the cluster.")),
    )
