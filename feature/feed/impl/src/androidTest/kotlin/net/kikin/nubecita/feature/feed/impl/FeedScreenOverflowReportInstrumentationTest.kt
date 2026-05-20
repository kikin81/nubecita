package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasContentDescription
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
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumentation pin-down for oftc.3 (nubecita-6buu): the PostCard
 * overflow's "Report post" row, tapped on a feed-side PostCard, pushes a
 * [Report] sub-route onto the active tab's stack via
 * `LocalMainShellNavState.current.add(...)`. The Report NavKey carries a
 * [ReportSubject.Post] with the tapped post's URI + CID — the same shape
 * `ModerationNavigationModule`'s `@MainShell` provider resolves to a
 * Modal Bottom Sheet hosting the report dialog.
 *
 * Mirrors the precedent set by `SearchPostsScreenInstrumentationTest`:
 * construct [MainShellNavState] directly via its public primary
 * constructor (per its kdoc's "Direct construction (tests only)" note),
 * with [Feed] as the sole top-level route. The assertion target is
 * `navState.backStack` — the same snapshot list the production inner
 * `NavDisplay` reads. Asserting the dialog's actual render is covered by
 * `ReportDialogScreenInstrumentationTest` inside `:feature:moderation:impl`;
 * standing the dialog up here would force a NavDisplay + Hilt-injected
 * EntryProviderInstaller multibinding harness for no incremental
 * contract gain.
 *
 * Like `FeedScreenInstrumentationTest`, the repository is faked via
 * `@TestInstallIn`-replaced `FeedRepositoryModule` (see
 * [net.kikin.nubecita.feature.feed.impl.testing.TestFeedRepositoryModule])
 * so this never touches the real network / auth stack.
 */
@HiltAndroidTest
class FeedScreenOverflowReportInstrumentationTest {
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
                startRoute = Feed,
                topLevelKeyState = mutableStateOf(Feed),
                backStacks = mapOf<NavKey, NavBackStack<NavKey>>(Feed to NavBackStack(Feed)),
            )
    }

    @Test
    fun overflowMenu_reportRow_pushesReportNavKeyOntoActiveTabStack() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalMainShellNavState provides navState) {
                NubecitaTheme {
                    FeedScreen()
                }
            }
        }

        // Wait for FakeFeedRepository's first page to land via
        // StateFlow → recomposition. Same waitUntil pattern as
        // FeedScreenInstrumentationTest.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(POST_ALICE_TEXT, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Sanity: stack starts at just the Feed tab home.
        assertEquals(listOf<NavKey>(Feed), navState.backStack.toList())

        // Tap the first PostCard's overflow affordance (contentDescription
        // "More options" — the design-system label set on the 5th action-
        // row cell by PostOverflowAffordance). useUnmergedTree because
        // the icon button's semantics are nested under the action row's
        // merged tree; without it Compose collapses the icon-button
        // semantics into the parent row and the matcher returns nothing.
        composeTestRule
            .onAllNodes(hasContentDescription(MORE_OPTIONS_CD), useUnmergedTree = true)[0]
            .performClick()
        composeTestRule.waitForIdle()

        // DropdownMenu is now open — tap "Report post". The displayed
        // text matches R.string.moderation_action_report_post exactly.
        composeTestRule.onNodeWithText(REPORT_POST_LABEL).performClick()

        // The screen's effect collector resolves the LaunchedEffect's
        // FeedEffect.NavigateTo branch by calling
        // LocalMainShellNavState.current.add(key); the test's navState
        // is the same instance bound via CompositionLocalProvider above,
        // so the push lands here. Allow time for the effect coroutine to
        // round-trip through the dispatcher.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            navState.backStack.any { it is Report }
        }

        val pushedReport = navState.backStack.last()
        assertTrue(
            "expected last back-stack key to be a Report sub-route, got $pushedReport",
            pushedReport is Report,
        )
        pushedReport as Report
        val subject = pushedReport.subject
        assertTrue(
            "expected Report subject to be a Post, got $subject",
            subject is ReportSubject.Post,
        )
        subject as ReportSubject.Post
        // FakeFeedRepository's first post is alice; the URI + CID below
        // mirror its DEFAULT_TIMELINE entry verbatim — if FakeFeedRepository
        // is re-shaped, update the constants together.
        assertEquals(POST_ALICE_URI, subject.uri)
        assertEquals(POST_ALICE_CID, subject.cid)
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L

        // Mirror of FakeFeedRepository.DEFAULT_TIMELINE's first post —
        // duplicated here as constants because the fake's `singlePost`
        // helper is private (test pins the expected URI + CID
        // explicitly so a fake reshuffle surfaces as a test failure
        // instead of a silent semantic drift).
        const val POST_ALICE_TEXT = "Hello world from alice"
        const val POST_ALICE_URI = "at://did:plc:alice/app.bsky.feed.post/post1"
        const val POST_ALICE_CID = "bafyreitest1"

        // Hardcoded mirror of the design-system string
        // `R.string.postcard_action_more` and the moderation-impl string
        // `R.string.moderation_action_report_post`. Compose tests can't
        // resolve string resources from another module's R class without
        // instrumentation context plumbing, and pinning the literals
        // here makes a string change show up as a test failure rather
        // than a silent semantic drift. Same pattern as the existing
        // FeedScreenInstrumentationTest's REPLY_CD constant.
        const val MORE_OPTIONS_CD = "More options"
        const val REPORT_POST_LABEL = "Report post"
    }
}
