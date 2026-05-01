package net.kikin.nubecita.feature.feed.impl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
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

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
        const val POST_ALICE_TEXT = "Hello world from alice"
        const val POST_BOB_TEXT = "Bluesky is fun"
        const val POST_CAROL_TEXT = "Three posts in this feed"
    }
}
