package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.feedcache.FeedAnchor
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.testing.FakeFeedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * The reader's place, through the screen plumbing: recorded when a scroll
 * settles, and applied when the feed opens with one stored.
 *
 * The halves are asserted separately rather than as one continuous journey,
 * because restoring happens when a feed is *opened* and a Compose test cannot
 * cheaply give the screen a second ViewModel store. Between them they cover
 * everything above the DAO — the snapshot-flow recorder, the debounce, the
 * event, the repository call, the restore target, and the scroll — and the
 * DAO half has its own tests in `:core:database`.
 */
@HiltAndroidTest
class FeedScreenAnchorInstrumentationTest {
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
    fun aSettledScroll_recordsWhereTheReaderStopped() {
        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen()
                }
            }
        }
        awaitPost(0)

        composeTestRule.onNodeWithTag(FeedTestTags.LIST).performScrollToIndex(SCROLL_TARGET)

        // The recorder is debounced: a fling writes once when it settles
        // rather than ~100 times on the way down.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { fake.savedAnchor != null }

        val (key, _) = requireNotNull(fake.savedAnchor)
        assertNotNull(key)
        // The key of a row, not an index: that is what survives a session.
        assertEquals(true, key.startsWith("at://did:plc:scroll/app.bsky.feed.post/"))
    }

    @Test
    fun aStoredPlace_opensTheFeedThere() {
        val target = FakeFeedRepository.posts(FIXTURE_SIZE)[SCROLL_TARGET].key
        fake.storedAnchor = FeedAnchor(position = SCROLL_TARGET, itemKey = target, offset = 0)

        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen()
                }
            }
        }

        // The row the reader stopped on is on screen without them scrolling,
        // which is the whole feature.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(postText(SCROLL_TARGET), substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText(postText(SCROLL_TARGET), substring = true).assertIsDisplayed()
    }

    @Test
    fun openingTheFeed_doesNotBehaveLikeAPullToRefresh() {
        // The regression this whole epic exists to prevent. Launch used to
        // mean "fetch the top"; it now means "show me where I was", and only
        // the gesture is destructive (D8). If opening ever cleared the place
        // again, every session would start at the top with no symptom in any
        // unit test.
        val target = FakeFeedRepository.posts(FIXTURE_SIZE)[SCROLL_TARGET].key
        fake.storedAnchor = FeedAnchor(position = SCROLL_TARGET, itemKey = target, offset = 0)

        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen()
                }
            }
        }
        awaitPost(SCROLL_TARGET)

        assertEquals(false, fake.anchorCleared)
        assertEquals(0, fake.refreshCalls)
    }

    @Test
    fun pullToRefresh_dropsTheReadersPlace() {
        // The one destructive path (D8). The gesture means "I am done with
        // this, give me a clean timeline", so keeping the place and restoring
        // into the old one would be the opposite of what was asked.
        //
        // No stored anchor here on purpose: the gesture has to be made from
        // the top of the list, and restoring one would scroll away from it.
        // The assertion holds either way — the gesture drops the place
        // whether or not there was one to drop.
        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen()
                }
            }
        }
        awaitPost(0)

        composeTestRule.onNodeWithTag(FeedTestTags.LIST).performTouchInput {
            swipeDown(startY = centerY, endY = bottom)
        }

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { fake.anchorCleared }
        assertEquals(true, fake.anchorCleared)
    }

    private fun awaitPost(index: Int) {
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(postText(index), substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun postText(index: Int) = "scroll fixture post number $index"

    private companion object {
        const val FIXTURE_SIZE = 20
        const val SCROLL_TARGET = 12
        const val WAIT_TIMEOUT_MILLIS = 10_000L
    }
}
