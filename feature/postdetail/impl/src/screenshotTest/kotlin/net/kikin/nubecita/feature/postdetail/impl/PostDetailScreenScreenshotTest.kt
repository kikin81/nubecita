package net.kikin.nubecita.feature.postdetail.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.feature.postdetail.impl.data.ThreadItem
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Screenshot baselines for `PostDetailScreen`'s render-state matrix:
 * initial-loading, three initial-error variants (Network, NotFound,
 * Unauthenticated), loaded, and loaded+refreshing — each in light + dark.
 * Driven through `PostDetailScreenContent` directly with fixture
 * `PostDetailState` inputs so the captures are deterministic across
 * machines (no Hilt graph, no live network, no animation).
 */

@PreviewTest
@Preview(name = "initial-loading-light", showBackground = true)
@Preview(name = "initial-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenInitialLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailScreenScreenshotHost(
            state = PostDetailState(loadStatus = PostDetailLoadStatus.InitialLoading),
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-network-light", showBackground = true)
@Preview(name = "initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenInitialErrorNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailScreenScreenshotHost(
            state =
                PostDetailState(
                    loadStatus = PostDetailLoadStatus.InitialError(PostDetailError.Network),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-notfound-light", showBackground = true)
@Preview(name = "initial-error-notfound-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenInitialErrorNotFoundScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailScreenScreenshotHost(
            state =
                PostDetailState(
                    loadStatus = PostDetailLoadStatus.InitialError(PostDetailError.NotFound),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-unauth-light", showBackground = true)
@Preview(name = "initial-error-unauth-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenInitialErrorUnauthScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailScreenScreenshotHost(
            state =
                PostDetailState(
                    loadStatus = PostDetailLoadStatus.InitialError(PostDetailError.Unauthenticated),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-light", showBackground = true)
@Preview(name = "loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailScreenScreenshotHost(
            state = PostDetailState(items = fixtureThread(), loadStatus = PostDetailLoadStatus.Idle),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-refreshing-light", showBackground = true)
@Preview(name = "loaded-refreshing-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenLoadedRefreshingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailScreenScreenshotHost(
            state = PostDetailState(items = fixtureThread(), loadStatus = PostDetailLoadStatus.Refreshing),
        )
    }
}

/**
 * The visual contract m28.5.2 ships: Focus Post on `surfaceContainerHigh`
 * with 24dp rounded corners, sat between Ancestor(s) and Reply rows on
 * the default `surface` background. Pure ancestor → focus → replies
 * shape (no Blocked / NotFound inline siblings) so the fixture's only
 * purpose is locking the container-color + shape contrast.
 *
 * Per `add-postdetail-m3-expressive-treatment` design Decision 6, the
 * contrast pair MUST be captured in BOTH light and dark themes — light-
 * only or dark-only would let an asymmetry (crisp in one mode, washed
 * out in the other) ship without explicit human review.
 */
@PreviewTest
@Preview(name = "container-hierarchy-light", showBackground = true)
@Preview(name = "container-hierarchy-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailScreenContainerHierarchyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostDetailScreenScreenshotHost(
            state = PostDetailState(items = containerHierarchyThread(), loadStatus = PostDetailLoadStatus.Idle),
        )
    }
}

@Composable
private fun PostDetailScreenScreenshotHost(state: PostDetailState) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Provide a fixed clock so PostCard's relative-time label is
    // deterministic — pairs with FIXTURE_CREATED_AT below to render "2h"
    // forever. No Clock.System.now() reads happen anywhere in this file.
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        PostDetailScreenContent(
            state = state,
            snackbarHostState = snackbarHostState,
            callbacks = PostCallbacks.None,
            onBack = {},
            onRetry = {},
            onRefresh = {},
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
    text: String = "Fixture post $id — sample content for the post-detail screenshot matrix.",
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

/**
 * Mixed thread fixture covering every rendered ThreadItem variant in
 * m28.5.1's mapper output: Ancestor → Focus → inline Blocked sibling →
 * two Reply rows. Fold is reserved for m28.5.2 and is omitted here.
 */
private fun fixtureThread(): ImmutableList<ThreadItem> =
    persistentListOf<ThreadItem>(
        ThreadItem.Ancestor(post = fixturePost("ancestor", text = "Ancestor — what kicked off the thread.")),
        ThreadItem.Focus(post = fixturePost("focus", text = "Focused post — the one tapped from the feed.")),
        ThreadItem.Blocked(uri = "at://did:plc:blocked/app.bsky.feed.post/blocked"),
        ThreadItem.Reply(post = fixturePost("reply-1", text = "Top-level reply — direct child of the focus."), depth = 1),
        ThreadItem.Reply(post = fixturePost("reply-2", text = "Another top-level reply — sibling of reply-1."), depth = 1),
    )

/**
 * Clean ancestor → focus → replies thread for the container-hierarchy
 * contrast fixture. No Blocked / NotFound siblings so the rendered
 * surface is dominated by the surfaceContainerHigh ↔ surface contrast
 * the m28.5.2 visual treatment is locking.
 */
private fun containerHierarchyThread(): ImmutableList<ThreadItem> =
    persistentListOf<ThreadItem>(
        ThreadItem.Ancestor(post = fixturePost("ancestor", text = "Ancestor — what kicked off the thread.")),
        ThreadItem.Focus(post = fixturePost("focus", text = "Focused post — sits on surfaceContainerHigh with 24dp rounded corners.")),
        ThreadItem.Reply(post = fixturePost("reply-1", text = "Top-level reply — direct child of the focus."), depth = 1),
        ThreadItem.Reply(post = fixturePost("reply-2", text = "Another top-level reply — sibling of reply-1."), depth = 1),
    )
