package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.common.navigation.rememberMainShellNavState
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.search.api.Search
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * Verifies that an entry's **state** — not just its key — survives while its
 * tab is off screen (nubecita-owmt.2).
 *
 * `MainShellPersistenceTest` and `MainShellNavStateTest` only assert which keys
 * are on the stacks, which is why the original bug went unnoticed: the keys
 * came back after a tab switch while every ViewModel and `rememberSaveable`
 * value behind them had been cleared. These tests read the state itself,
 * through the same `rememberDecoratedEntries` + `NavDisplay(entries = …)`
 * wiring `MainShell` uses.
 */
@RunWith(AndroidJUnit4::class)
class MainShellEntryStateRetentionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nonStartTab_keepsSavedStateAndViewModel_acrossTabSwitch() {
        val stateRef = AtomicReference<MainShellNavState>()
        composeTestRule.setContent { RetentionHarness(stateRef) }

        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Search) }
        repeat(3) { composeTestRule.onNodeWithTag(INCREMENT_TAG).performClick() }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("Search saved=3")
        composeTestRule.onNodeWithTag(VIEW_MODEL_TAG).assertTextEquals("Search vm=3")

        // Leave Search for another non-start tab, then for the start tab, then return.
        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Chats) }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("Chats saved=0")
        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Feed) }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("Feed saved=0")
        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Search) }

        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("Search saved=3")
        composeTestRule.onNodeWithTag(VIEW_MODEL_TAG).assertTextEquals("Search vm=3")
    }

    @Test
    fun subRouteOnNonStartTab_keepsState_acrossTabSwitch() {
        val stateRef = AtomicReference<MainShellNavState>()
        composeTestRule.setContent { RetentionHarness(stateRef) }

        composeTestRule.runOnIdle {
            stateRef.get().addTopLevel(Search)
            stateRef.get().add(DETAIL)
        }
        repeat(2) { composeTestRule.onNodeWithTag(INCREMENT_TAG).performClick() }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=2")

        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Chats) }
        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Search) }

        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=2")
        composeTestRule.onNodeWithTag(VIEW_MODEL_TAG).assertTextEquals("PostDetailRoute vm=2")
    }

    @Test
    fun poppedEntry_losesItsState() {
        val stateRef = AtomicReference<MainShellNavState>()
        composeTestRule.setContent { RetentionHarness(stateRef) }

        composeTestRule.runOnIdle {
            stateRef.get().addTopLevel(Search)
            stateRef.get().add(DETAIL)
        }
        repeat(2) { composeTestRule.onNodeWithTag(INCREMENT_TAG).performClick() }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=2")

        // A real pop must still clear the entry: pushing the same route again starts fresh.
        composeTestRule.runOnIdle { stateRef.get().removeLast() }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("Search saved=0")
        composeTestRule.runOnIdle { stateRef.get().add(DETAIL) }

        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=0")
        composeTestRule.onNodeWithTag(VIEW_MODEL_TAG).assertTextEquals("PostDetailRoute vm=0")
    }

    @Test
    fun sameRouteOnTwoTabs_keepsIndependentState() {
        val stateRef = AtomicReference<MainShellNavState>()
        composeTestRule.setContent { RetentionHarness(stateRef) }

        composeTestRule.runOnIdle {
            stateRef.get().addTopLevel(Search)
            stateRef.get().add(DETAIL)
        }
        repeat(2) { composeTestRule.onNodeWithTag(INCREMENT_TAG).performClick() }

        composeTestRule.runOnIdle {
            stateRef.get().addTopLevel(Chats)
            stateRef.get().add(DETAIL)
        }
        // Chats' copy of the route is its own entry, not a view onto Search's.
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=0")
        composeTestRule.onNodeWithTag(VIEW_MODEL_TAG).assertTextEquals("PostDetailRoute vm=0")
        composeTestRule.onNodeWithTag(INCREMENT_TAG).performClick()

        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Search) }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=2")
        composeTestRule.runOnIdle { stateRef.get().addTopLevel(Chats) }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=1")
    }

    @Test
    fun sameRouteTwiceOnOneTab_keepsIndependentState() {
        val stateRef = AtomicReference<MainShellNavState>()
        composeTestRule.setContent { RetentionHarness(stateRef) }

        // Search: [Search, DETAIL, OTHER_DETAIL, DETAIL] — `add` allows a route to recur
        // when it is not already on top.
        composeTestRule.runOnIdle {
            stateRef.get().addTopLevel(Search)
            stateRef.get().add(DETAIL)
        }
        repeat(2) { composeTestRule.onNodeWithTag(INCREMENT_TAG).performClick() }
        composeTestRule.runOnIdle {
            stateRef.get().add(OTHER_DETAIL)
            stateRef.get().add(DETAIL)
        }

        // The second DETAIL is a new screen, not the first one resurfacing.
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=0")
        composeTestRule.onNodeWithTag(VIEW_MODEL_TAG).assertTextEquals("PostDetailRoute vm=0")
        composeTestRule.onNodeWithTag(INCREMENT_TAG).performClick()

        // Popping back down reaches the first DETAIL exactly as it was left.
        composeTestRule.runOnIdle {
            stateRef.get().removeLast()
            stateRef.get().removeLast()
        }
        composeTestRule.onNodeWithTag(SAVED_TAG).assertTextEquals("PostDetailRoute saved=2")
        composeTestRule.onNodeWithTag(VIEW_MODEL_TAG).assertTextEquals("PostDetailRoute vm=2")
    }
}

/** Counts in a ViewModel so the test can tell a retained instance from a recreated one. */
internal class CounterViewModel : ViewModel() {
    var count by mutableIntStateOf(0)
}

/**
 * `MainShell`'s inner `NavDisplay` wiring with fake entry bodies: each entry
 * shows a `rememberSaveable` counter and a ViewModel counter, both bumped by
 * one button, labelled with the route's simple name.
 */
@Composable
private fun RetentionHarness(stateRef: AtomicReference<MainShellNavState>) {
    val state =
        rememberMainShellNavState(
            startRoute = Feed,
            topLevelRoutes = listOf(Feed, Search, Chats),
        )
    SideEffect { stateRef.set(state) }

    val provider =
        entryProvider<NavKey> {
            entry<Feed> { CounterBody("Feed") }
            entry<Search> { CounterBody("Search") }
            entry<Chats> { CounterBody("Chats") }
            entry<PostDetailRoute> { CounterBody("PostDetailRoute") }
        }
    val entries =
        state.rememberDecoratedEntries(
            entryProvider = provider,
            entryDecorators = {
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                )
            },
        )
    NavDisplay(entries = entries, onBack = { state.removeLast() })
}

@Composable
private fun CounterBody(
    label: String,
    viewModel: CounterViewModel = viewModel(),
) {
    var saved by rememberSaveable { mutableIntStateOf(0) }
    Column {
        Text(text = "$label saved=$saved", modifier = Modifier.testTag(SAVED_TAG))
        Text(text = "$label vm=${viewModel.count}", modifier = Modifier.testTag(VIEW_MODEL_TAG))
        Button(
            onClick = {
                saved++
                viewModel.count++
            },
            modifier = Modifier.testTag(INCREMENT_TAG),
        ) {
            Text("increment")
        }
    }
}

private val DETAIL = PostDetailRoute(postUri = "at://did:plc:fake/app.bsky.feed.post/abc123")
private val OTHER_DETAIL = PostDetailRoute(postUri = "at://did:plc:fake/app.bsky.feed.post/xyz789")

private const val SAVED_TAG = "saved"
private const val VIEW_MODEL_TAG = "viewModel"
private const val INCREMENT_TAG = "increment"
