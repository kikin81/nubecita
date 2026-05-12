package net.kikin.nubecita.feature.profile.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Smoke instrumentation test: tapping Edit on the actions row surfaces
 * the Coming Soon snackbar. Renders [ProfileScreenContent] directly
 * with a hand-built [ProfileScreenViewState] — no ViewModel, no Hilt
 * graph, no real network.
 *
 * The host's effect-to-snackbar mapping lives in [ProfileScreen] (not
 * [ProfileScreenContent]), so the test re-creates the minimal slice
 * of that mapping locally: capture every dispatched [ProfileEvent],
 * and when [ProfileEvent.EditTapped] arrives, show the same snackbar
 * copy the production host shows for [ProfileEffect.ShowComingSoon] /
 * [StubbedAction.Edit]. This proves two things in one pass:
 *
 *   1. The actions-row Edit button click dispatches `EditTapped`
 *      (assertion on the captured events list).
 *   2. The `SnackbarHost` rendered by [ProfileScreenContent] surfaces
 *      the expected message when the host's effect collector triggers
 *      it (assertion on the snackbar text node).
 *
 * Per the run-instrumented label rule (memory
 * `feedback_run_instrumented_label_on_androidtest_prs`), the PR
 * must carry the `run-instrumented` label so CI's instrumented job
 * fires.
 */
class ProfileScreenInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editTap_surfacesComingSoonSnackbar() {
        // Resolve copy from resources rather than hardcoding so the test
        // survives localization changes — same string the production
        // host uses for ShowComingSoon(StubbedAction.Edit).
        val context = composeTestRule.activity
        val editLabel = context.getString(R.string.profile_action_edit)
        val expectedSnackbarText = context.getString(R.string.profile_snackbar_edit_coming_soon)
        val capturedEvents = mutableListOf<ProfileEvent>()

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val onEvent: (ProfileEvent) -> Unit = { event ->
                    capturedEvents += event
                    if (event is ProfileEvent.EditTapped) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(expectedSnackbarText)
                        }
                    }
                }
                ProfileScreenContent(
                    state = sampleOwnProfileState(),
                    listState = rememberLazyListState(),
                    snackbarHostState = snackbarHostState,
                    postCallbacks = PostCallbacks.None,
                    onEvent = onEvent,
                )
            }
        }

        composeTestRule.onNodeWithText(editLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(expectedSnackbarText).assertIsDisplayed()
        // JUnit assertTrue (not the JVM `assert` keyword) — `assert(...)`
        // is gated on `-ea` which is typically OFF on Android instrumentation
        // runners, so the captured-events check would silently skip.
        assertTrue(
            "Edit tap MUST dispatch ProfileEvent.EditTapped; captured: $capturedEvents",
            capturedEvents.contains(ProfileEvent.EditTapped),
        )
    }

    private fun sampleOwnProfileState(): ProfileScreenViewState =
        ProfileScreenViewState(
            handle = null,
            header =
                ProfileHeaderUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice",
                    avatarUrl = null,
                    bannerUrl = null,
                    avatarHue = 217,
                    bio = null,
                    location = null,
                    website = null,
                    joinedDisplay = "Joined April 2023",
                    postsCount = 0L,
                    followersCount = 0L,
                    followsCount = 0L,
                ),
            ownProfile = true,
            viewerRelationship = ViewerRelationship.Self,
            selectedTab = ProfileTab.Posts,
            postsStatus =
                TabLoadStatus.Loaded(
                    items = persistentListOf(),
                    isAppending = false,
                    isRefreshing = false,
                    hasMore = false,
                    cursor = null,
                ),
            repliesStatus = TabLoadStatus.Idle,
            mediaStatus = TabLoadStatus.Idle,
        )
}
