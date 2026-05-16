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
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.search.impl.testing.FakeSearchActorsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Reference instrumentation test for `nubecita-vrba.9`'s tap-through nav:
 * tapping an actor hit on the Search People tab pushes a [Profile] onto
 * the active tab's stack via `LocalMainShellNavState.current.add(...)`.
 *
 * Repository fake + `MainShellNavState` construction mirror
 * [SearchPostsScreenInstrumentationTest]. The People tab has no
 * single-top duplicate-tap concern at the search layer (consecutive
 * taps on the same actor structurally equal the existing top of stack,
 * which `MainShellNavState.add` already drops), so the corresponding
 * single-top guard is covered there.
 */
@HiltAndroidTest
class SearchActorsScreenInstrumentationTest {
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
    fun actorTap_pushesProfileRouteOntoActiveTabStack() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalMainShellNavState provides navState) {
                NubecitaTheme {
                    SearchActorsScreen(
                        currentQuery = "alice",
                        onClearQuery = {},
                        onShowAppendError = {},
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(FakeSearchActorsRepository.ACTOR_ALICE_NAME, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Sanity: stack starts at just the Search tab home.
        assertEquals(listOf<NavKey>(Search), navState.backStack.toList())

        // ActorRow has a top-level clickable; tapping the displayName text
        // fires the row's onClick → SearchActorsEvent.ActorTapped.
        composeTestRule
            .onNodeWithText(FakeSearchActorsRepository.ACTOR_ALICE_NAME, substring = true)
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            navState.backStack.any { it is Profile }
        }

        assertEquals(
            listOf<NavKey>(Search, Profile(handle = FakeSearchActorsRepository.ACTOR_ALICE_HANDLE)),
            navState.backStack.toList(),
        )
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
