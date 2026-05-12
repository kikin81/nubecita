package net.kikin.nubecita.feature.profile.impl

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
import net.kikin.nubecita.feature.profile.api.Profile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 11 fixtures × 2 themes = 22 screenshot baselines covering the full
 * ProfileScreenContent surface — hero variants (with/without banner,
 * loading, error), Posts-tab variants (loaded, loading, empty, error),
 * and Replies-tab variants (loaded, empty, error). Compact width only;
 * Medium-width fixtures land in Bead F once the ListDetailSceneStrategy
 * metadata is wired.
 */
private val SAMPLE_HEADER =
    ProfileHeaderUi(
        did = "did:plc:alice",
        handle = "alice.bsky.social",
        displayName = "Alice",
        avatarUrl = null,
        bannerUrl = null,
        avatarHue = 217,
        bio = "Designer · lima → barcelona · she/her",
        location = "Lima, Peru",
        website = "alice.example.com",
        joinedDisplay = "Joined April 2023",
        postsCount = 412,
        followersCount = 2_142,
        followsCount = 342,
    )

private val SAMPLE_HEADER_WITH_BANNER =
    SAMPLE_HEADER.copy(
        // Banner URL is just a string; BoldHeroGradient handles the
        // null/blank short-circuit. We don't actually fetch in screenshot
        // tests (Coil is no-op'd in Layoutlib) — the gradient renders
        // the avatarHue fallback for both cases. The visual delta with
        // a real banner can only be verified on-device.
        bannerUrl = "https://cdn.example.com/banner.jpg",
    )

private val EMPTY_LIST = persistentListOf<TabItemUi>()

private fun sampleLoadedState(): ProfileScreenViewState =
    ProfileScreenViewState(
        handle = null,
        header = SAMPLE_HEADER,
        ownProfile = true,
        viewerRelationship = ViewerRelationship.Self,
        selectedTab = ProfileTab.Posts,
        postsStatus =
            TabLoadStatus.Loaded(
                items =
                    persistentListOf(
                        TabItemUi.Post(samplePostUi("a")),
                        TabItemUi.Post(samplePostUi("b")),
                        TabItemUi.Post(samplePostUi("c")),
                    ),
                isAppending = false,
                isRefreshing = false,
                hasMore = false,
                cursor = null,
            ),
        repliesStatus = TabLoadStatus.Idle,
        mediaStatus = TabLoadStatus.Idle,
    )

private fun samplePostUi(suffix: String): PostUi =
    PostUi(
        id = "post-$suffix",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:preview-$suffix",
                handle = "preview-$suffix.bsky.social",
                displayName = "Preview $suffix",
                avatarUrl = null,
            ),
        // 2h before PREVIEW_NOW so the relative label renders as "2h".
        createdAt = Instant.parse("2026-04-26T10:00:00Z"),
        text = "Preview post $suffix — sample content for screenshot fixtures.",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 12),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

// Fixed instants for previews + screenshots. Paired with [FixtureClock],
// the rendered PostCard relative-time label is "2h" forever — no
// `Clock.System.now()` involved, so screenshots don't drift as wall-clock
// advances. Mirrors the convention in :feature:feed:impl/FeedScreen.kt
// and FeedScreenScreenshotTest.kt.
private val PREVIEW_NOW = Instant.parse("2026-04-26T12:00:00Z")

private object FixtureClock : Clock {
    override fun now(): Instant = PREVIEW_NOW
}

@Composable
private fun ProfileScreenContentHost(state: ProfileScreenViewState) {
    NubecitaTheme(dynamicColor = false) {
        CompositionLocalProvider(LocalClock provides FixtureClock) {
            ProfileScreenContent(
                state = state,
                listState = rememberLazyListState(),
                snackbarHostState = remember { SnackbarHostState() },
                postCallbacks = PostCallbacks.None,
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "screen-hero-with-banner-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-hero-with-banner-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroWithBannerScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(header = SAMPLE_HEADER_WITH_BANNER),
    )
}

@PreviewTest
@Preview(name = "screen-hero-without-banner-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-hero-without-banner-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroWithoutBannerScreenshot() {
    ProfileScreenContentHost(sampleLoadedState())
}

@PreviewTest
@Preview(name = "screen-hero-loading-light", showBackground = true, heightDp = 800)
@Preview(name = "screen-hero-loading-dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroLoadingScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            header = null,
            headerError = null,
            postsStatus = TabLoadStatus.InitialLoading,
        ),
    )
}

@PreviewTest
@Preview(name = "screen-hero-error-light", showBackground = true, heightDp = 800)
@Preview(name = "screen-hero-error-dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroErrorScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            header = null,
            headerError = ProfileError.Network,
            postsStatus = TabLoadStatus.Idle,
        ),
    )
}

@PreviewTest
@Preview(name = "screen-posts-loaded-light", showBackground = true, heightDp = 1600)
@Preview(name = "screen-posts-loaded-dark", showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsLoadedScreenshot() {
    ProfileScreenContentHost(sampleLoadedState())
}

@PreviewTest
@Preview(name = "screen-posts-loading-light", showBackground = true, heightDp = 1600)
@Preview(name = "screen-posts-loading-dark", showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsLoadingScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(postsStatus = TabLoadStatus.InitialLoading),
    )
}

@PreviewTest
@Preview(name = "screen-posts-empty-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-posts-empty-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsEmptyScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            postsStatus =
                TabLoadStatus.Loaded(
                    items = EMPTY_LIST,
                    isAppending = false,
                    isRefreshing = false,
                    hasMore = false,
                    cursor = null,
                ),
        ),
    )
}

@PreviewTest
@Preview(name = "screen-posts-error-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-posts-error-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsErrorScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(postsStatus = TabLoadStatus.InitialError(ProfileError.Network)),
    )
}

@PreviewTest
@Preview(name = "screen-replies-loaded-light", showBackground = true, heightDp = 1600)
@Preview(name = "screen-replies-loaded-dark", showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenRepliesLoadedScreenshot() {
    // Replies tab selected; repliesStatus holds 3 reply PostCards.
    // postsStatus / mediaStatus stay Idle — irrelevant when selectedTab = Replies.
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            selectedTab = ProfileTab.Replies,
            repliesStatus =
                TabLoadStatus.Loaded(
                    items =
                        persistentListOf(
                            TabItemUi.Post(samplePostUi("reply-a")),
                            TabItemUi.Post(samplePostUi("reply-b")),
                            TabItemUi.Post(samplePostUi("reply-c")),
                        ),
                    isAppending = false,
                    isRefreshing = false,
                    hasMore = false,
                    cursor = null,
                ),
        ),
    )
}

@PreviewTest
@Preview(name = "screen-replies-empty-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-replies-empty-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenRepliesEmptyScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            selectedTab = ProfileTab.Replies,
            repliesStatus =
                TabLoadStatus.Loaded(
                    items = EMPTY_LIST,
                    isAppending = false,
                    isRefreshing = false,
                    hasMore = false,
                    cursor = null,
                ),
        ),
    )
}

@PreviewTest
@Preview(name = "screen-replies-error-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-replies-error-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenRepliesErrorScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            selectedTab = ProfileTab.Replies,
            repliesStatus = TabLoadStatus.InitialError(ProfileError.Network),
        ),
    )
}

// ─── Other-user variants ─────────────────────────────────────────────────────

@PreviewTest
@Preview(name = "screen-other-user-follow-light", showBackground = true, heightDp = 1100)
@Preview(
    name = "screen-other-user-follow-dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenOtherUserFollowScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            handle = "bob.bsky.social",
            header = SAMPLE_HEADER.copy(handle = "bob.bsky.social", displayName = "Bob"),
            ownProfile = false,
            viewerRelationship = ViewerRelationship.NotFollowing,
        ),
    )
}

@PreviewTest
@Preview(name = "screen-other-user-following-light", showBackground = true, heightDp = 1100)
@Preview(
    name = "screen-other-user-following-dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenOtherUserFollowingScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            handle = "bob.bsky.social",
            header = SAMPLE_HEADER.copy(handle = "bob.bsky.social", displayName = "Bob"),
            ownProfile = false,
            viewerRelationship = ViewerRelationship.Following,
        ),
    )
}

// ─── Medium-width two-pane fixture ────────────────────────────────────────────

/**
 * Renders [ProfileScreenContent] inside a Navigation 3 [NavDisplay] driven
 * by [rememberListDetailSceneStrategy], mirroring the production wiring in
 * [MainShell]. At widthDp = 800 the two-pane directive splits the canvas:
 * Profile fills the list pane; [PostDetailPaneEmptyState] fills the detail
 * pane. Compact-only fixtures remain in the variants above.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@PreviewTest
@Preview(
    name = "screen-medium-two-pane-empty-light",
    showBackground = true,
    widthDp = 800,
    heightDp = 800,
)
@Preview(
    name = "screen-medium-two-pane-empty-dark",
    showBackground = true,
    widthDp = 800,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenMediumTwoPaneEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        val backStack = remember { mutableStateListOf<NavKey>(Profile(handle = null)) }
        val sceneStrategy =
            rememberListDetailSceneStrategy<NavKey>(
                directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfoV2()),
            )
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) },
            sceneStrategies = listOf(sceneStrategy),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider =
                entryProvider {
                    entry<Profile>(
                        metadata =
                            ListDetailSceneStrategy.listPane(
                                detailPlaceholder = { PostDetailPaneEmptyState() },
                            ),
                    ) {
                        CompositionLocalProvider(LocalClock provides FixtureClock) {
                            ProfileScreenContent(
                                state = sampleLoadedState(),
                                listState = rememberLazyListState(),
                                snackbarHostState = remember { SnackbarHostState() },
                                postCallbacks = PostCallbacks.None,
                                onEvent = {},
                            )
                        }
                    }
                },
        )
    }
}
