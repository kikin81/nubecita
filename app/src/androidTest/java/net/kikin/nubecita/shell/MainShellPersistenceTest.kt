package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.core.common.navigation.rememberMainShellNavState
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that `MainShellNavState`'s persistence mechanism (the
 * `rememberSerializable` + `rememberNavBackStack` pair inside
 * `rememberMainShellNavState`) survives a configuration change /
 * `saveInstanceState → recreate()` round trip.
 *
 * Closes nubecita-3it as a side effect — process-death persistence of
 * the active tab and per-tab back stacks falls out of adopting the Nav3
 * `multiple-backstacks` recipe shape.
 *
 * Uses `StateRestorationTester` rather than a full `ActivityScenario` —
 * the persistence behavior under test is driven by Compose's saveable
 * machinery, which `StateRestorationTester` exercises identically (and
 * without needing a real `MainActivity` + auth flow + Hilt graph).
 *
 * Runs locally today; will be picked up by CI when nubecita-16a (CI
 * emulator runner) lands.
 */
@RunWith(AndroidJUnit4::class)
class MainShellPersistenceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainShellNavState_survivesStateRestoration() {
        val tester = StateRestorationTester(composeTestRule)
        var mutationTrigger by mutableStateOf(false)

        tester.setContent {
            val state =
                rememberMainShellNavState(
                    startRoute = Feed,
                    topLevelRoutes = listOf(Feed, Search, Chats, Profile(handle = null)),
                )

            LaunchedEffect(mutationTrigger) {
                if (mutationTrigger) {
                    state.addTopLevel(Search)
                    state.add(Profile(handle = "alice.bsky.social"))
                }
            }

            Column {
                Text(
                    text = state.topLevelKey::class.simpleName ?: "null",
                    modifier = Modifier.testTag("activeKey"),
                )
                Text(
                    text = state.backStack.size.toString(),
                    modifier = Modifier.testTag("backStackSize"),
                )
                Text(
                    text =
                        state.backStack.joinToString(",") {
                            it::class.simpleName ?: "?"
                        },
                    modifier = Modifier.testTag("backStackContents"),
                )
            }
        }

        // Pre-mutation: active is Feed, stack is just [Feed].
        composeTestRule.onNodeWithTag("activeKey").assertTextEquals("Feed")
        composeTestRule.onNodeWithTag("backStackSize").assertTextEquals("1")

        // Trigger mutation: active becomes Search; Profile pushed onto Search's stack.
        // Flattened stack is [Feed (start), Search (top-level of active), Profile (sub-route)].
        composeTestRule.runOnIdle { mutationTrigger = true }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("activeKey").assertTextEquals("Search")
        composeTestRule.onNodeWithTag("backStackSize").assertTextEquals("3")
        composeTestRule.onNodeWithTag("backStackContents").assertTextEquals("Feed,Search,Profile")

        // Round-trip: simulate saveInstanceState + recreate().
        tester.emulateSavedInstanceStateRestore()

        // Post-restore: state holder is reconstructed from saved primitives.
        // Active tab still Search, stack still [Feed, Search, Profile].
        composeTestRule.onNodeWithTag("activeKey").assertTextEquals("Search")
        composeTestRule.onNodeWithTag("backStackSize").assertTextEquals("3")
        composeTestRule.onNodeWithTag("backStackContents").assertTextEquals("Feed,Search,Profile")
    }
}
