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
import net.kikin.nubecita.data.models.QuotedEmbedUi
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
@Preview(name = "loaded-with-self-thread-chain-light", showBackground = true)
@Composable
private fun FeedScreenLoadedWithSelfThreadChainScreenshot() {
    // Mixed fixture: standalone post → 3-post same-author chain →
    // standalone post. Locks the m28.4 contract — the chain renders as
    // ONE LazyColumn item (a Column of 3 PostCards) joined by a
    // continuous avatar-gutter connector line via Modifier.threadConnector.
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedItemsWithSelfThreadChain(),
                    isAppending = false,
                    isRefreshing = false,
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-self-thread-chain-with-quote-middle-light", showBackground = true)
@Composable
private fun FeedScreenSelfThreadChainQuoteMiddleScreenshot() {
    // Locks design Decision 6 of `add-feed-same-author-thread-chain`:
    // a chain whose middle post carries an `EmbedUi.Record` (quote post)
    // renders the quote chrome inside the body content slot WITHOUT
    // colliding with the avatar-gutter connector line. PostCard's
    // body/gutter geometry keeps the two surfaces apart by
    // construction; this fixture is the regression contract.
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedItemsWithSelfThreadChainAndQuoteMiddle(),
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

@PreviewTest
@Preview(name = "loaded-with-compose-fab-light", showBackground = true)
@Preview(name = "loaded-with-compose-fab-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadedWithComposeFabScreenshot() {
    // wtq.9 swapped the prior scroll-to-top FAB content for a compose-
    // new-post FAB. Visibility no longer depends on scroll position —
    // the FAB renders whenever viewState is Loaded, so this fixture
    // pins the resting state at scroll position 0 (same as
    // `loaded-light` / `loaded-dark`, which now also show the FAB
    // because of the gate change). Locks the visual contract for the
    // compose FAB: bottom-right Scaffold slot, Edit icon, primary tone.
    //
    // Light + Dark variants because the FAB's surface tonality differs
    // by theme and we want a regression catch for both.
    NubecitaTheme(dynamicColor = false) {
        FeedScreenScreenshotHost(
            viewState =
                FeedScreenViewState.Loaded(
                    feedItems = fixtureFeedItems(count = 6),
                    isAppending = false,
                    isRefreshing = false,
                ),
        )
    }
}

@Composable
private fun FeedScreenScreenshotHost(
    viewState: FeedScreenViewState,
    initialFirstVisibleItemIndex: Int = 0,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisibleItemIndex)
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

/**
 * `Single` → 3-post `SelfThreadChain` (all by the same author) →
 * `Single`. The chain renders as one LazyColumn item containing three
 * stacked `PostCard`s with `connectAbove` / `connectBelow` flags wired
 * by index, joined by a continuous avatar-gutter connector line.
 */
private fun fixtureFeedItemsWithSelfThreadChain(): ImmutableList<FeedItemUi> {
    val chainAuthor =
        AuthorUi(
            did = "did:plc:fixture-chain",
            handle = "chainauthor.bsky.social",
            displayName = "Chain Author",
            avatarUrl = null,
        )

    fun chainPost(
        id: String,
        text: String,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author = chainAuthor,
            createdAt = FIXTURE_CREATED_AT,
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(replyCount = 1, repostCount = 0, likeCount = 4),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
    return persistentListOf(
        FeedItemUi.Single(post = fixturePost("standalone-before", text = "A standalone post above the self-thread chain.")),
        FeedItemUi.SelfThreadChain(
            posts =
                persistentListOf(
                    chainPost("at://chain/1", "First post in the same-author chain — root of the self-thread."),
                    chainPost("at://chain/2", "Second post — replies to the first; same author."),
                    chainPost("at://chain/3", "Third post — replies to the second; chain leaf."),
                ),
        ),
        FeedItemUi.Single(post = fixturePost("standalone-after", text = "A standalone post below the chain.")),
    )
}

/**
 * 3-post chain whose middle post carries an `EmbedUi.Record` quote
 * embed. Locks the
 * `add-feed-same-author-thread-chain` design Decision 6 contract:
 * the avatar-gutter connector and the quote-post chrome are
 * geometrically isolated (gutter is left of the avatar; quote chrome
 * sits inside the body slot) and the two surfaces don't visually
 * collide.
 */
private fun fixtureFeedItemsWithSelfThreadChainAndQuoteMiddle(): ImmutableList<FeedItemUi> {
    val chainAuthor =
        AuthorUi(
            did = "did:plc:fixture-quoter",
            handle = "quoter.bsky.social",
            displayName = "Quote Chain",
            avatarUrl = null,
        )
    val quotedAuthor =
        AuthorUi(
            did = "did:plc:fixture-quoted",
            handle = "quoted.bsky.social",
            displayName = "Quoted Author",
            avatarUrl = null,
        )
    val quotedEmbed =
        EmbedUi.Record(
            quotedPost =
                net.kikin.nubecita.data.models.QuotedPostUi(
                    uri = "at://quoted/post/q",
                    cid = "bafyreifakequotedcid000000000000000000000000000",
                    author = quotedAuthor,
                    createdAt = FIXTURE_CREATED_AT,
                    text = "A quoted post by an unrelated author — the chain's middle post links to it.",
                    facets = persistentListOf(),
                    embed = QuotedEmbedUi.Empty,
                ),
        )

    fun chainPost(
        id: String,
        text: String,
        embed: EmbedUi = EmbedUi.Empty,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author = chainAuthor,
            createdAt = FIXTURE_CREATED_AT,
            text = text,
            facets = persistentListOf(),
            embed = embed,
            stats = PostStatsUi(replyCount = 1, repostCount = 0, likeCount = 7),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
    return persistentListOf(
        FeedItemUi.SelfThreadChain(
            posts =
                persistentListOf(
                    chainPost("at://qchain/1", "Chain root — plain text."),
                    chainPost(
                        id = "at://qchain/2",
                        text = "Middle post quotes a separate post.",
                        embed = quotedEmbed,
                    ),
                    chainPost("at://qchain/3", "Chain leaf — plain text after the quote."),
                ),
        ),
    )
}
