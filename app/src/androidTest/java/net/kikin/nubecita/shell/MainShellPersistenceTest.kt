package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.core.layout.WindowSizeClass
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.common.navigation.rememberMainShellNavState
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

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
 */
@RunWith(AndroidJUnit4::class)
class MainShellPersistenceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainShellNavState_survivesStateRestoration() {
        val tester = StateRestorationTester(composeTestRule)
        // Captured each composition via SideEffect so the test thread can
        // mutate the state holder once via `runOnIdle` instead of inside a
        // `LaunchedEffect`. A `LaunchedEffect`-based mutation would re-fire
        // after the restore-recreate cycle and double-push, leaving
        // `[Feed, Search, Profile, Profile]` (size 4) instead of the
        // expected size 3.
        val stateRef = AtomicReference<MainShellNavState>()

        tester.setContent {
            val state =
                rememberMainShellNavState(
                    startRoute = Feed,
                    topLevelRoutes = listOf(Feed, Search, Chats, Profile(handle = null)),
                )
            SideEffect { stateRef.set(state) }

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

        // Mutate once on the test thread: active becomes Search; Profile
        // pushed onto Search's stack. Flattened stack is
        // [Feed (start), Search (top-level of active), Profile (sub-route)].
        composeTestRule.runOnIdle {
            val state = checkNotNull(stateRef.get()) { "stateRef not initialized" }
            state.addTopLevel(Search)
            state.add(Profile(handle = "alice.bsky.social"))
        }
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

    /**
     * Verifies that `ListDetailSceneStrategy`'s pane state survives a
     * `medium → compact → medium` rotation round-trip when the back stack
     * is `[Feed]`. After both restorations the placeholder must be
     * composed in the right pane on medium, just as it was before the
     * round-trip.
     *
     * Width is injected by passing a synthetic [WindowAdaptiveInfo] into
     * [ListDetailHarness] rather than overriding `LocalConfiguration.screenWidthDp`.
     * `currentWindowAdaptiveInfoV2()` reads from `WindowMetrics`, not
     * `LocalConfiguration`, so the override approach was a silent no-op on
     * phone-class emulators (whose actual width is ~400dp = compact,
     * regardless of the override). Injecting `WindowAdaptiveInfo` directly
     * keeps the directive computation exactly matched with production
     * (`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`) while making
     * the width deterministic across emulator profiles.
     *
     * Uses a fake harness rather than the production `MainShell`: the
     * real `FeedDetailPlaceholder` is `internal` to `:feature:feed:impl`
     * and `MainShell` requires a Hilt graph to compose. The fake's role
     * is to drive the strategy with the same `listPane{}` metadata shape
     * the production wiring uses; the placeholder is identified by
     * [PLACEHOLDER_TAG] so the assertion is content-agnostic. Visual
     * correctness of the real placeholder is covered by
     * `FeedDetailPlaceholderScreenshotTest` in `:feature:feed:impl`.
     */
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun listDetailPlaceholder_survivesMediumToCompactToMediumRotation() {
        val tester = StateRestorationTester(composeTestRule)
        var adaptiveInfo by mutableStateOf(adaptiveInfoForWidth(MEDIUM_WIDTH_DP))

        tester.setContent {
            ListDetailHarness(windowAdaptiveInfo = adaptiveInfo)
        }

        // At medium width: placeholder is composed in the right pane.
        composeTestRule.onNodeWithTag(PLACEHOLDER_TAG).assertIsDisplayed()

        // Rotate to compact: strategy collapses to single-pane, placeholder
        // is no longer composed. Configuration changes in real Android trigger
        // an activity recreate, which `emulateSavedInstanceStateRestore` mirrors.
        adaptiveInfo = adaptiveInfoForWidth(COMPACT_WIDTH_DP)
        tester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PLACEHOLDER_TAG).assertDoesNotExist()

        // Rotate back to medium: strategy expands to two-pane again, and
        // because the back stack persisted (`[Feed]`) the placeholder
        // reappears in the right pane.
        adaptiveInfo = adaptiveInfoForWidth(MEDIUM_WIDTH_DP)
        tester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PLACEHOLDER_TAG).assertIsDisplayed()
    }

    /**
     * Verifies that a `detailPane()`-tagged entry on the back stack
     * survives a `medium → compact → medium` rotation round-trip:
     *
     * - At Medium: the detail entry renders in the right pane next to
     *   the `listPane{}` Feed entry.
     * - After rotating to Compact + restore: the strategy collapses to
     *   single-pane and renders the top of the stack (the detail entry)
     *   full-screen — the detail content is still visible, proving the
     *   back stack persisted across the saveInstanceState round-trip.
     * - After rotating back to Medium + restore: the strategy expands
     *   to two-pane and the detail entry is back in the right pane.
     *
     * Uses the real [PostDetailRoute] NavKey from `:feature:postdetail:api`
     * (already a transitive dep of `:app`'s androidTest source set) with a
     * fake content body — same pattern as the existing test using the real
     * `Feed` NavKey with a fake list body. The contract under test is
     * "an entry tagged `detailPane()` slots into the right pane on
     * Medium/Expanded and survives state restoration"; the real
     * `PostDetailScreen` would require a Hilt graph to compose and isn't
     * what the assertion is about.
     */
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun listDetailDetailPane_survivesMediumToCompactToMediumRotation() {
        val tester = StateRestorationTester(composeTestRule)
        var adaptiveInfo by mutableStateOf(adaptiveInfoForWidth(MEDIUM_WIDTH_DP))

        val detailRoute =
            PostDetailRoute(postUri = "at://did:plc:fake/app.bsky.feed.post/abc123")

        val fakeDetailInstaller: EntryProviderInstaller = {
            entry<PostDetailRoute>(
                metadata = ListDetailSceneStrategy.detailPane(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag(DETAIL_TAG),
                ) {
                    Text(text = "fake-detail-content")
                }
            }
        }

        tester.setContent {
            ListDetailHarness(
                windowAdaptiveInfo = adaptiveInfo,
                backStack = listOf(Feed, detailRoute),
                extraInstallers = listOf(fakeDetailInstaller),
            )
        }

        // Medium: detail content visible in the right pane.
        composeTestRule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

        // Rotate to Compact + restore: strategy collapses to single-pane,
        // top-of-stack (the detail entry) renders full-screen — content
        // survived the recreate.
        adaptiveInfo = adaptiveInfoForWidth(COMPACT_WIDTH_DP)
        tester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

        // Rotate back to Medium + restore: strategy expands back to
        // two-pane, detail content slots into the right pane again.
        adaptiveInfo = adaptiveInfoForWidth(MEDIUM_WIDTH_DP)
        tester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()
    }
}

/**
 * Build a [WindowAdaptiveInfo] for the given [widthDp] — width is what the
 * strategy under test branches on; height + posture are stable defaults
 * because nothing in the test reads them.
 *
 * Uses [WindowSizeClass.compute] (not the raw constructor) because
 * `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` does
 * exact-equality matching against the breakpoint constants
 * (0/600/840/1200/1600 dp). `compute` buckets the requested width down
 * to the nearest breakpoint so a 360 dp request becomes the Compact
 * bucket (minWidthDp=0), matching what the framework produces at runtime.
 * Constructing `WindowSizeClass(minWidthDp = 360, ...)` directly would
 * miss every `when` arm and fall into the L/XL `else` branch (3 panes).
 */
private fun adaptiveInfoForWidth(widthDp: Int): WindowAdaptiveInfo =
    WindowAdaptiveInfo(
        windowSizeClass =
            WindowSizeClass.compute(
                dpWidth = widthDp.toFloat(),
                dpHeight = HEIGHT_DP.toFloat(),
            ),
        windowPosture = Posture(),
    )

/**
 * Test harness that mirrors `MainShell`'s inner `NavDisplay` wiring: a
 * back stack of `[Feed]`, a real `ListDetailSceneStrategy`, and an entry
 * registered with `listPane{}` metadata + a placeholder tagged
 * [PLACEHOLDER_TAG] for assertion. Production decorators
 * (`SaveableStateHolderNavEntryDecorator`,
 * `ViewModelStoreNavEntryDecorator`) are included so the saved-state
 * machinery exercised by `StateRestorationTester` matches the real
 * `MainShell`.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@androidx.compose.runtime.Composable
private fun ListDetailHarness(
    windowAdaptiveInfo: WindowAdaptiveInfo,
    backStack: List<NavKey> = listOf(Feed),
    extraInstallers: List<EntryProviderInstaller> = emptyList(),
) {
    val backStackState: SnapshotStateList<NavKey> =
        remember { mutableStateListOf<NavKey>().apply { addAll(backStack) } }
    val sceneStrategy =
        rememberListDetailSceneStrategy<NavKey>(
            directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(windowAdaptiveInfo),
        )

    val fakeFeedInstaller: EntryProviderInstaller = {
        entry<Feed>(
            metadata =
                ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .testTag(PLACEHOLDER_TAG),
                        ) {
                            Text(text = "fake-detail-placeholder")
                        }
                    },
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(text = "fake-feed-list")
            }
        }
    }

    NavDisplay(
        backStack = backStackState,
        onBack = { if (backStackState.isNotEmpty()) backStackState.removeAt(backStackState.lastIndex) },
        sceneStrategies = listOf(sceneStrategy),
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                fakeFeedInstaller()
                extraInstallers.forEach { it() }
            },
    )
}

private const val PLACEHOLDER_TAG = "list-detail-placeholder"
private const val DETAIL_TAG = "list-detail-detail"
private const val COMPACT_WIDTH_DP = 360
private const val MEDIUM_WIDTH_DP = 600
private const val HEIGHT_DP = 800
