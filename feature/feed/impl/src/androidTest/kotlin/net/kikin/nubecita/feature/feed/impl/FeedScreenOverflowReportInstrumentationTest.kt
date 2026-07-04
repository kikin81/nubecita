package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
import net.kikin.nubecita.designsystem.R as DesignsystemR

/**
 * Instrumentation pin-down for oftc.3 (nubecita-6buu): the PostCard
 * overflow's "Report post" row, tapped on a feed-side PostCard, emits
 * `FeedEffect.NavigateTo(Report(...))` from the VM. The screen's effect
 * collector routes the key out via its `onNavigateTo: (NavKey) -> Unit`
 * callback — the same host hook that `FeedNavigationModule` wires to
 * `LocalMainShellNavState.current.add(key)` in production. The Report
 * NavKey carries a [ReportSubject.Post] with the tapped post's URI +
 * CID — the same shape `ModerationNavigationModule`'s `@MainShell`
 * provider resolves to a Modal Bottom Sheet hosting the report dialog.
 *
 * The test constructs [MainShellNavState] directly via its public
 * primary constructor (per its kdoc's "Direct construction (tests only)"
 * note) and wires `onNavigateTo = { key -> navState.add(key) }` — the
 * exact lambda `FeedNavigationModule` uses. The assertion target is
 * `navState.backStack`, the same snapshot list the production inner
 * `NavDisplay` reads. The screen stays host-agnostic per the Nav3
 * modular-hilt recipe (screens take callbacks; hosts wire navState).
 *
 * Asserting the dialog's actual render is covered by
 * `ReportDialogScreenInstrumentationTest` inside
 * `:feature:moderation:impl`; standing the dialog up here would force a
 * NavDisplay + Hilt-injected EntryProviderInstaller multibinding harness
 * for no incremental contract gain.
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
        // Resolve UI strings from `:designsystem` (where they live) via
        // the live activity rather than hardcoding English literals.
        // Keeps the assertion locale-safe and survives string-resource
        // edits. Mirrors `ReportDialogScreenInstrumentationTest`'s
        // `composeTestRule.activity.getString(...)` pattern.
        val moreOptionsCd =
            composeTestRule.activity.getString(DesignsystemR.string.postcard_action_more)
        val reportPostLabel =
            composeTestRule.activity.getString(DesignsystemR.string.moderation_action_report_post)

        composeTestRule.setContent {
            // FeedScreen takes a generic `(NavKey) -> Unit` for tab-internal
            // sub-routes; the host (FeedNavigationModule) wires it to
            // `navState.add(key)` in production. The test wires the same shape
            // — verifies the screen routes the effect to the callback rather
            // than re-implementing host knowledge.
            //
            // `LocalMainShellNavState` MUST be provided: the shared
            // `rememberPostInteractions` helper reads it unconditionally to
            // route block/report navigation (compositionLocalOf with an
            // `error(...)` default), so without this the first composition
            // crashes before any post renders.
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen(onNavigateTo = { key -> navState.add(key) })
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

        // Tap the first PostCard's overflow affordance. `PostStat` labels
        // its non-toggleable action cells (reply / repost / share / overflow)
        // via `onClickLabel` (see PostStat's kdoc), NOT `contentDescription`,
        // so match on the OnClick action label — `hasContentDescription`
        // never matches the overflow button. useUnmergedTree because the
        // icon button's semantics are nested under the action row's merged
        // tree.
        composeTestRule
            .onAllNodes(hasClickLabel(moreOptionsCd), useUnmergedTree = true)[0]
            .performClick()
        composeTestRule.waitForIdle()

        // DropdownMenu is now open — tap "Report post". The displayed
        // text resolves to `R.string.moderation_action_report_post` from
        // `:designsystem` (where PostCard.kt reads it).
        composeTestRule.onNodeWithText(reportPostLabel).performClick()

        // The screen's effect collector resolves the
        // `FeedEffect.NavigateTo` branch by invoking the `onNavigateTo`
        // callback wired above — which lands directly on `navState.add`.
        // Allow time for the effect coroutine to round-trip through the
        // dispatcher.
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

        /**
         * Matches a node whose `OnClick` action carries [label] as its
         * accessibility label. `PostStat` labels its non-toggleable action
         * cells (reply / repost / share / overflow) via `onClickLabel`, not
         * `contentDescription`, so this is the correct selector for the
         * PostCard overflow affordance.
         */
        fun hasClickLabel(label: String) =
            SemanticsMatcher("OnClick action label == '$label'") { node ->
                node.config.getOrNull(SemanticsActions.OnClick)?.label == label
            }

        // Mirror of FakeFeedRepository.DEFAULT_TIMELINE's first post —
        // duplicated here as constants because the fake's `singlePost`
        // helper is private (test pins the expected URI + CID
        // explicitly so a fake reshuffle surfaces as a test failure
        // instead of a silent semantic drift).
        const val POST_ALICE_TEXT = "Hello world from alice"
        const val POST_ALICE_URI = "at://did:plc:alice/app.bsky.feed.post/post1"
        const val POST_ALICE_CID = "bafyreitest1"
    }
}
