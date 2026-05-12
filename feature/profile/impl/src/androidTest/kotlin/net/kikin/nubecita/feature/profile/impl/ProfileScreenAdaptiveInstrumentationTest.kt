package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostDetailPaneEmptyState
import net.kikin.nubecita.feature.profile.api.Profile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the Medium-width two-pane behavior driven by
 * [ListDetailSceneStrategy]. On a forced Medium-bucket width the strategy
 * splits the canvas so that the list pane and the detail-pane placeholder
 * ([PostDetailPaneEmptyState]) render simultaneously.
 *
 * Width is injected via a synthetic [WindowAdaptiveInfo] rather than
 * overriding `LocalConfiguration.screenWidthDp`, because
 * `currentWindowAdaptiveInfoV2()` reads from `WindowMetrics` — the
 * configuration override is a silent no-op on phone-class emulators.
 * Injecting [WindowAdaptiveInfo] directly keeps the directive computation
 * exactly matched with production
 * ([calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth]) while making
 * the width deterministic across emulator profiles. Pattern comes from
 * [net.kikin.nubecita.shell.MainShellPersistenceTest].
 *
 * The list-pane content is a lightweight fake (a single `Text` node with a
 * known test tag) rather than the real [ProfileScreenContent], so the test
 * focuses purely on the strategy's pane-splitting behavior — not on the
 * profile screen's internal state. Functional correctness of the real screen
 * at Compact width is covered by [ProfileScreenInstrumentationTest].
 *
 * Per the run-instrumented label rule (memory
 * `feedback_run_instrumented_label_on_androidtest_prs`), the PR must carry
 * the `run-instrumented` label so CI's instrumented job fires.
 */
@HiltAndroidTest
class ProfileScreenAdaptiveInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun mediumWidth_listPane_andDetailPaneEmptyState_renderSimultaneously() {
        // Inject a Medium-bucket WindowAdaptiveInfo so the directive fires
        // two-pane mode regardless of the real physical device width.
        val mediumAdaptiveInfo =
            WindowAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        dpWidth = MEDIUM_WIDTH_DP.toFloat(),
                        dpHeight = HEIGHT_DP.toFloat(),
                    ),
                windowPosture = Posture(),
            )

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                val backStack = remember { mutableStateListOf<NavKey>(Profile(handle = null)) }
                val sceneStrategy =
                    rememberListDetailSceneStrategy<NavKey>(
                        directive =
                            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                                mediumAdaptiveInfo,
                            ),
                    )
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
                    },
                    sceneStrategies = listOf(sceneStrategy),
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider =
                        entryProvider {
                            entry<Profile>(
                                metadata =
                                    ListDetailSceneStrategy.listPane(
                                        detailPlaceholder = { PostDetailPaneEmptyState() },
                                    ),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .testTag(LIST_PANE_TAG),
                                ) {
                                    Text(text = PROFILE_LIST_PANE_TEXT)
                                }
                            }
                        },
                )
            }
        }

        // Both panes must be composed and visible simultaneously on a
        // Medium-width canvas — this is the core invariant under test.
        composeTestRule.onNodeWithTag(LIST_PANE_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(SELECT_POST_TEXT, substring = true)
            .assertIsDisplayed()
    }

    companion object {
        private const val LIST_PANE_TAG = "profile-list-pane"
        private const val PROFILE_LIST_PANE_TEXT = "PROFILE_LIST_PANE"

        /** Matches `R.string.nubecita_detail_pane_select_post` — "Select a post to read". */
        private const val SELECT_POST_TEXT = "Select a post to read"

        private const val MEDIUM_WIDTH_DP = 600
        private const val HEIGHT_DP = 800
    }
}
