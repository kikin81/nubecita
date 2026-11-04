package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.feedcache.FeedAnchor
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.impl.testing.FakeFeedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * A custom feed gets none of this: no pill, no seam, no kept place (D14).
 *
 * `getFeed` takes no `since`, so a generator cannot be asked for "what
 * arrived while I was away" at all — it refreshes on open, which means its
 * reader is always already at the newest content. Offering them an affordance
 * for a backlog that cannot exist would be the worst kind of wrong: it would
 * look like it worked.
 *
 * This covers the surface. The two gates themselves are pinned where they
 * live — `FeedViewModelTest` for the pill's flow and `:core:feed-cache`'s
 * `FeedRepositoryTest` for the anchor, which no fake can stand in for.
 */
@HiltAndroidTest
class FeedViewScreenFollowingOnlyInstrumentationTest {
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
    fun aCustomFeed_showsNoPillAndKeepsNoPlace() {
        // Both inputs are deliberately present: faces to show, and a place to
        // restore. A surface that wired either of them up would pass with
        // them absent and fail here.
        fake.newPosts.value =
            persistentListOf(AuthorUi(did = "did:plc:nova", handle = "nova.astro", displayName = "Nova", avatarUrl = null))
        fake.storedAnchor = FeedAnchor(position = SCROLL_TARGET, itemKey = FakeFeedRepository.posts(FIXTURE_SIZE)[SCROLL_TARGET].key, offset = 0)

        composeTestRule.setContent {
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedViewScreen(feedUri = FEED_URI, displayName = "Cat pics")
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText("scroll fixture post number 0", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(NEW_POSTS_LABEL).assertDoesNotExist()
        composeTestRule.onNodeWithTag(FeedTestTags.LIST).performScrollToIndex(SCROLL_TARGET)
        composeTestRule.waitForIdle()

        // No seam either — nothing can write one for a feed that cannot
        // fetch a `since` range.
        composeTestRule.onNodeWithText(SEAM_LOAD_LABEL).assertDoesNotExist()
        // And a settled scroll records nothing: the surface never asks.
        assertNull(fake.savedAnchor)
        assertEquals(0, fake.catchUpCalls)
    }

    private companion object {
        const val FIXTURE_SIZE = 20
        const val SCROLL_TARGET = 12
        const val WAIT_TIMEOUT_MILLIS = 10_000L
        const val FEED_URI = "at://did:plc:gen/app.bsky.feed.generator/cats"
        const val NEW_POSTS_LABEL = "New posts"
        const val SEAM_LOAD_LABEL = "Load the posts in between"
    }
}
