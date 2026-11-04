package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.feedcache.CachedFeedRow
import net.kikin.nubecita.core.feedcache.CatchUpOutcome
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.testing.FakeFeedRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import javax.inject.Inject

/**
 * The seam, through the screen plumbing: it comes from a marker row in the
 * projection, it fills when the reader reaches it, it goes away when the range
 * closes, and at the cap it offers the only way across.
 *
 * Every case places the marker itself rather than leaving a hole in the
 * positions, because that distinction is the point: a reader who mutes an
 * author leaves a hole too, and a feed that drew a break there would be
 * reporting something that never happened.
 */
@HiltAndroidTest
class FeedScreenSeamInstrumentationTest {
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
    }

    @Test
    fun aMarkerRow_rendersTheSeam() {
        fake.rows = withSeam(at = 1, exhausted = false)

        show()
        awaitPost(0)

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { isDisplayed(SEAM_LOAD_LABEL) }
        composeTestRule.onNodeWithText(SEAM_LOAD_LABEL).assertIsDisplayed()
    }

    @Test
    fun noMarkerRow_noSeam() {
        // The negative half, and the reason the marker exists. These posts
        // come from one page with nothing missing between them; a feed that
        // inferred a seam from anything else would draw one here.
        fake.items = FakeFeedRepository.posts(FIXTURE_SIZE)

        show()
        awaitPost(0)

        composeTestRule.onNodeWithText(SEAM_LOAD_LABEL).assertDoesNotExist()
        composeTestRule.onNodeWithText(SEAM_CAPPED_LABEL).assertDoesNotExist()
        assertEquals(0, fake.catchUpCalls)
    }

    @Test
    fun reachingTheSeam_fillsItExactlyOnce() {
        // Proximity is composition: a seam this far down is not composed when
        // the screen opens, so the fill is the reader arriving rather than the
        // screen loading.
        fake.rows = withSeam(at = SCROLL_TARGET, exhausted = false)
        fake.onCatchUp = { Result.success(CatchUpOutcome.NOT_POSSIBLE) }

        show()
        awaitPost(0)
        assertEquals(0, fake.catchUpCalls)

        composeTestRule.onNodeWithTag(FeedTestTags.LIST).performScrollToIndex(SCROLL_TARGET)

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { fake.catchUpCalls == 1 }
        composeTestRule.waitForIdle()
        // One per crossing. The row recomposes as the list settles and as the
        // fill's state lands, and each of those recompositions asking again
        // would be a prepend per frame.
        assertEquals(1, fake.catchUpCalls)
    }

    @Test
    fun anExhaustedRange_leavesNoSeamBehind() {
        // The fill closed the gap, so the break it described is gone. Leaving
        // the row would tell the reader there is still something missing.
        fake.rows = withSeam(at = 1, exhausted = false)
        fake.onCatchUp = {
            fake.items = FakeFeedRepository.posts(FIXTURE_SIZE)
            Result.success(CatchUpOutcome.CAUGHT_UP)
        }

        show()
        awaitPost(0)

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { !isDisplayed(SEAM_LOAD_LABEL) }
        composeTestRule.onNodeWithText(SEAM_LOAD_LABEL).assertDoesNotExist()
    }

    @Test
    fun anInterruptedFill_stillShowsASeam() {
        // The seam is a row in the database, so a fill that died mid-flight
        // leaves it exactly where it was — and the screen has to come back
        // offering the fill again rather than a spinner that never resolves or
        // a break with nothing to act on.
        fake.rows = withSeam(at = 1, exhausted = false)
        fake.onCatchUp = { Result.failure(IOException("interrupted")) }

        show()
        awaitPost(0)

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { isDisplayed(SEAM_LOAD_LABEL) }
        composeTestRule.onNodeWithText(SEAM_LOAD_LABEL).assertIsDisplayed()
    }

    @Test
    fun atTheCap_theSeamOffersJumpToNewest() {
        // D5: past the page cap no amount of filling closes the gap, so the
        // affordance changes rather than the row disappearing — and it says
        // what it is, because a bare visual break is the one thing the seam
        // exists to avoid.
        fake.rows = withSeam(at = 1, exhausted = true)

        show()
        awaitPost(0)

        composeTestRule.onNodeWithText(SEAM_CAPPED_LABEL).assertIsDisplayed()
        composeTestRule.onNodeWithText(SEAM_JUMP_LABEL).assertIsDisplayed()
        // Nothing to fill, so nothing was asked for.
        assertEquals(0, fake.catchUpCalls)
    }

    @Test
    fun tappingJumpToNewest_takesTheDestructivePath() {
        // The only honest way across a gap wider than the feed keeps: replace
        // the partition (D8), which also means dropping the place the reader
        // was in, since it belonged to the partition being replaced.
        fake.rows = withSeam(at = 1, exhausted = true)

        show()
        awaitPost(0)

        composeTestRule.onNodeWithText(SEAM_JUMP_LABEL).performClick()

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) { fake.anchorCleared }
        assertEquals(true, fake.anchorCleared)
    }

    private fun show() {
        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen()
                }
            }
        }
    }

    /** The fixture timeline with a marker row spliced in at [at]. */
    private fun withSeam(
        at: Int,
        exhausted: Boolean,
    ): List<CachedFeedRow> {
        val posts = FakeFeedRepository.posts(FIXTURE_SIZE).map(CachedFeedRow::Item)
        return posts.take(at) + CachedFeedRow.Seam(position = -250, exhausted = exhausted) + posts.drop(at)
    }

    private fun isDisplayed(text: String): Boolean =
        composeTestRule
            .onAllNodes(hasText(text))
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun awaitPost(index: Int) {
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText("scroll fixture post number $index", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        const val FIXTURE_SIZE = 20
        const val SCROLL_TARGET = 12
        const val WAIT_TIMEOUT_MILLIS = 10_000L

        /** Mirror `R.string.feed_seam_*`; the row's text is what the reader acts on. */
        const val SEAM_LOAD_LABEL = "Load the posts in between"
        const val SEAM_CAPPED_LABEL = "There is more here than we keep"
        const val SEAM_JUMP_LABEL = "Jump to newest"
    }
}
