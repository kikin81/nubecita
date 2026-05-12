package net.kikin.nubecita.feature.profile.impl

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
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
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 8 fixtures × 2 themes = 16 screenshot baselines covering the full
 * ProfileScreenContent surface — hero variants (with/without banner,
 * loading, error) and Posts-tab variants (loaded, loading, empty,
 * error). Compact width only; Medium-width fixtures land in Bead F
 * once the ListDetailSceneStrategy metadata is wired.
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
