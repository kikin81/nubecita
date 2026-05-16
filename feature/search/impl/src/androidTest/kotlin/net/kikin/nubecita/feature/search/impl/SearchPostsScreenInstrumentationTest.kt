package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.search.impl.testing.FakeSearchPostsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Reference instrumentation test for `nubecita-vrba.9`'s tap-through nav:
 * tapping a post hit on the Search Posts tab pushes a [PostDetailRoute]
 * onto the active tab's stack via `LocalMainShellNavState.current.add(...)`.
 *
 * The repository is faked via Hilt's `@TestInstallIn` replacement of
 * `SearchPostsRepositoryModule` (see [net.kikin.nubecita.feature.search.impl.testing.TestSearchPostsRepositoryModule])
 * so the test never touches the real network or auth stack. The
 * authenticated XrpcClient flow is covered separately in `:core:auth`'s
 * androidTest.
 *
 * `MainShellNavState` is constructed directly via its public primary
 * constructor (per its kdoc's "Direct construction (tests only)" note),
 * with [Search] as the sole top-level route. Tests assert against
 * `navState.backStack` after the tap — the same snapshot list the
 * production inner `NavDisplay` reads.
 */
@HiltAndroidTest
class SearchPostsScreenInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navState: MainShellNavState

    @Before
    fun setUp() {
        hiltRule.inject()
        navState =
            MainShellNavState(
                startRoute = Search,
                topLevelKeyState = mutableStateOf(Search),
                backStacks = mapOf<NavKey, NavBackStack<NavKey>>(Search to NavBackStack(Search)),
            )
    }

    @Test
    fun postTap_pushesPostDetailRouteOntoActiveTabStack() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalMainShellNavState provides navState) {
                NubecitaTheme {
                    SearchPostsScreen(
                        currentQuery = "alice",
                        onClearQuery = {},
                        onShowAppendError = {},
                    )
                }
            }
        }

        // The VM's first-page fetch resolves via the synchronous fake repo,
        // but the result still has to land through StateFlow -> recomposition.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(FakeSearchPostsRepository.POST_ALICE_TEXT, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Sanity: stack starts at just the Search tab home.
        assertEquals(listOf<NavKey>(Search), navState.backStack.toList())

        // PostCard's outer Column has clickable { callbacks.onTap(post) }.
        // Tapping the post text fires it (Compose merges the body content
        // into the clickable's semantics tree).
        composeTestRule
            .onNodeWithText(FakeSearchPostsRepository.POST_ALICE_TEXT, substring = true)
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            navState.backStack.any { it is PostDetailRoute }
        }

        assertEquals(
            listOf<NavKey>(Search, PostDetailRoute(postUri = FakeSearchPostsRepository.POST_ALICE_URI)),
            navState.backStack.toList(),
        )
    }

    @Test
    fun doubleTapOnSamePost_doesNotStackDuplicatePostDetailRoutes() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalMainShellNavState provides navState) {
                NubecitaTheme {
                    SearchPostsScreen(
                        currentQuery = "alice",
                        onClearQuery = {},
                        onShowAppendError = {},
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(FakeSearchPostsRepository.POST_ALICE_TEXT, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // First tap lands the PostDetailRoute on the stack.
        composeTestRule
            .onNodeWithText(FakeSearchPostsRepository.POST_ALICE_TEXT, substring = true)
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            navState.backStack.size == 2
        }

        // Second tap on the same row would push a structurally-equal
        // PostDetailRoute. MainShellNavState.add is single-top against
        // structural equality, so the second push is a no-op. Without that
        // guard, the user could stack N copies of the same focused post by
        // tapping repeatedly on the search hit row.
        composeTestRule
            .onNodeWithText(FakeSearchPostsRepository.POST_ALICE_TEXT, substring = true)
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            listOf<NavKey>(Search, PostDetailRoute(postUri = FakeSearchPostsRepository.POST_ALICE_URI)),
            navState.backStack.toList(),
        )
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
