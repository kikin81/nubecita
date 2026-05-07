package net.kikin.nubecita.feature.feed.impl

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * First reference instrumentation test of epic `nubecita-9tw` Phase 1.
 * Verifies that [FeedScreen] renders the posts returned by its
 * [net.kikin.nubecita.feature.feed.impl.data.FeedRepository] dependency.
 *
 * The repository is faked via Hilt's `@TestInstallIn` replacement of
 * `FeedRepositoryModule` (see [net.kikin.nubecita.feature.feed.impl.testing.TestFeedRepositoryModule])
 * so the test never touches the real network or auth stack. The
 * authenticated XrpcClient flow is covered separately in
 * `:core:auth/src/androidTest/` (nubecita-z9d) where it belongs.
 *
 * Test placement is intentional: per the modern Android multi-module
 * convention (Now in Android pattern), instrumentation tests live in
 * the module that owns the code under test. `FeedScreen` stays
 * `internal` to `:feature:feed:impl`; this test calls it directly
 * because it lives in the same Gradle module.
 */
@HiltAndroidTest
class FeedScreenInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun feedScreen_rendersFakedTimelinePosts() {
        composeTestRule.setContent {
            NubecitaTheme {
                FeedScreen()
            }
        }

        // FeedScreen fires FeedEvent.Load on first composition; the load
        // bounces through viewModelScope -> StateFlow -> recomposition.
        // waitUntil is the recommended replacement for IdlingResource on
        // StateFlow-driven UI (Android docs: "Alternatives to Idling
        // Resources in Compose tests").
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(POST_ALICE_TEXT, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText(POST_ALICE_TEXT, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(POST_BOB_TEXT, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(POST_CAROL_TEXT, substring = true).assertIsDisplayed()
    }

    /**
     * Pin-down for unified-composer step 10 (nubecita-wtq.10): the per-post
     * reply icon on every PostCard fires the screen-level `onReplyClick`
     * lambda with `PostUi.id` (the post's AT URI), and does NOT pass through
     * `FeedViewModel`. The host (`FeedNavigationModule`) wires this lambda
     * to `LocalComposerLauncher.current` for the width-conditional dispatch
     * (route push at Compact, Dialog overlay at Medium / Expanded).
     *
     * Asserts:
     *  - the affordance is present on every loaded post (3 posts → 3 reply
     *    nodes), so no special-casing of which posts are replyable;
     *  - tapping the first reply icon dispatches `onReplyClick` with the
     *    URI of the first faked post (`POST_ALICE_URI`), proving URI
     *    propagation through PostCard → PostCallbacks → FeedScreen lambda.
     */
    @Test
    fun replyTapOnPost_dispatchesOnReplyClick_withPostUri() {
        val capturedReplyUris = mutableListOf<String>()

        composeTestRule.setContent {
            NubecitaTheme {
                FeedScreen(onReplyClick = { uri -> capturedReplyUris += uri })
            }
        }

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(POST_ALICE_TEXT, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Affordance is present on every loaded post — the reply icon's
        // contentDescription is the design-system string "Reply" (set via
        // `R.string.postcard_action_reply` inside PostCard's ActionRow).
        // useUnmergedTree because the icon button's semantics are nested
        // under the action row's merged tree; without it Compose would
        // collapse the count to the parent row.
        composeTestRule
            .onAllNodes(hasContentDescription(REPLY_CD), useUnmergedTree = true)
            .assertCountEquals(3)

        // Tap the first reply icon (post1 = alice) and assert the callback
        // fired with post1's AT URI. The list order is fixed by
        // FakeFeedRepository (alice → bob → carol), so index 0 is alice.
        composeTestRule
            .onAllNodes(hasContentDescription(REPLY_CD), useUnmergedTree = true)[0]
            .performClick()

        assertEquals(listOf(POST_ALICE_URI), capturedReplyUris)
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
        const val POST_ALICE_TEXT = "Hello world from alice"
        const val POST_BOB_TEXT = "Bluesky is fun"
        const val POST_CAROL_TEXT = "Three posts in this feed"
        const val POST_ALICE_URI = "at://did:plc:alice/app.bsky.feed.post/post1"

        // Hardcoded mirror of the design-system string
        // `R.string.postcard_action_reply` ("Reply"). PostCard sets this
        // as the icon button's accessibilityLabel; Compose tests can't
        // resolve string resources from another module's R class
        // without instrumentation context plumbing, and pinning the
        // literal here makes a string change show up as a test failure
        // instead of a silent semantic drift.
        const val REPLY_CD = "Reply"
    }
}
