package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.core.videofeed.VideoFeedPage
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.testing.FakeFeedRepository
import net.kikin.nubecita.feature.feed.impl.ui.TrendingVideosViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Where the Discover "Trending Videos" carousel ends up relative to the
 * viewport.
 *
 * The carousel is a keyed header row that only exists once its thumbnails have
 * loaded, and the posts and the thumbnails come from two independent requests.
 * `LazyListState` follows the first visible row's key across a data change, so
 * a header inserted above it lands above the viewport: present in the list,
 * unseen until the reader scrolls up (nubecita-3duc). Nothing in a unit test
 * sees that — it is a property of the laid-out list — so it is asserted here.
 */
@HiltAndroidTest
class FeedScreenTrendingCarouselInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    internal lateinit var feedRepository: FeedRepository

    private lateinit var navState: MainShellNavState

    private val fake: FakeFeedRepository get() = feedRepository as FakeFeedRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        navState =
            MainShellNavState(
                startRoute = Feed,
                topLevelKeyState = mutableStateOf(Feed),
                backStacks = mapOf<NavKey, NavBackStack<NavKey>>(Feed to NavBackStack(Feed)),
            )
        fake.items = FakeFeedRepository.posts(FIXTURE_SIZE)
    }

    @Test
    fun carouselArrivingAfterThePosts_takesTheTopOfTheList() {
        // The order that hides it: the posts are laid out first, and the
        // thumbnails land afterwards.
        val page = CompletableDeferred<Result<VideoFeedPage>>()
        val trending = TrendingVideosViewModel(sourceReturning { page.await() })
        setDiscoverContent(trending)
        awaitPost(0)
        val firstPostTopBefore = firstPostTop()

        page.complete(Result.success(TRENDING_PAGE))
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { trending.thumbs.value.isNotEmpty() }
        awaitCarousel()

        // Asserted as geometry, not as "is displayed". The list draws behind
        // the top bar, so a carousel parked above the first post still has a
        // sliver inside the window and passes a displayed-check while being
        // exactly as invisible as the bug report says. What a reader sees is
        // whether the carousel took the first post's place and pushed it down.
        val thumb =
            composeTestRule
                .onAllNodesWithTag(FeedTestTags.TRENDING_VIDEO_THUMB)
                .onFirst()
                .getUnclippedBoundsInRoot()
        val pushedDownBy = firstPostTop() - firstPostTopBefore
        assertTrue(
            "the first post should sit below the carousel, but it moved by $pushedDownBy " +
                "for a carousel ${thumb.bottom - thumb.top} tall",
            pushedDownBy >= thumb.bottom - thumb.top,
        )
    }

    @Test
    fun carouselArrivingWhileTheReaderIsDownTheFeed_leavesThemThere() {
        // The other half of the same rule: only a list resting at the top is
        // kept at the top. A reader who has scrolled is looking at something,
        // and a header arriving above them is not a reason to take it away.
        val page = CompletableDeferred<Result<VideoFeedPage>>()
        val trending = TrendingVideosViewModel(sourceReturning { page.await() })
        setDiscoverContent(trending)
        awaitPost(0)
        composeTestRule.onNodeWithTag(FeedTestTags.LIST).performScrollToIndex(SCROLL_TARGET)
        awaitPost(SCROLL_TARGET)

        page.complete(Result.success(TRENDING_PAGE))
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { trending.thumbs.value.isNotEmpty() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(postText(SCROLL_TARGET), substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(FeedTestTags.TRENDING_VIDEO_THUMB).assertCountEquals(0)
    }

    private fun setDiscoverContent(trending: TrendingVideosViewModel) {
        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen(selectedFeedUri = DISCOVER_FEED_URI, trendingViewModel = trending)
                }
            }
        }
    }

    private fun awaitPost(index: Int) {
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(postText(index), substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** Waits for the carousel to be composed — which says nothing about where it is. */
    private fun awaitCarousel() {
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodesWithTag(FeedTestTags.TRENDING_VIDEO_THUMB)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun firstPostTop() =
        composeTestRule
            .onNodeWithText(postText(0), substring = true)
            .getUnclippedBoundsInRoot()
            .top

    private fun postText(index: Int) = "scroll fixture post number $index"

    private companion object {
        const val FIXTURE_SIZE = 20
        const val SCROLL_TARGET = 12
        const val WAIT_TIMEOUT_MILLIS = 10_000L

        /** Bluesky's Discover feed — the one `FeedScreen` hangs the carousel on. */
        const val DISCOVER_FEED_URI = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"

        val TRENDING_PAGE = VideoFeedPage(items = listOf(videoPost("a"), videoPost("b"), videoPost("c")), cursor = null)

        fun sourceReturning(load: suspend () -> Result<VideoFeedPage>): VideoFeedSourceFactory =
            object : VideoFeedSourceFactory {
                override fun create(authorDid: String?): VideoFeedSource =
                    object : VideoFeedSource {
                        override suspend fun loadPage(cursor: String?): Result<VideoFeedPage> = load()
                    }
            }

        fun videoPost(id: String): PostUi =
            PostUi(
                id = "at://did:plc:trending/app.bsky.feed.post/$id",
                cid = "bafyreitrending$id",
                author = AuthorUi(did = "did:plc:trending", handle = "t.bsky.social", displayName = "T", avatarUrl = null),
                createdAt = Instant.parse("2026-07-18T12:00:00Z"),
                text = "",
                facets = persistentListOf(),
                embed =
                    EmbedUi.Video(
                        posterUrl = null,
                        playlistUrl = "https://cdn.example/$id.m3u8",
                        aspectRatio = 0.56f,
                        durationSeconds = 8,
                        altText = null,
                    ),
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
    }
}
