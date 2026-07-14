package net.kikin.nubecita.feature.profile.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import org.junit.Assert.assertEquals
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
                    onBack = null,
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

    /**
     * Tapping the Settings icon button on own-profile emits
     * [ProfileEvent.SettingsTapped] (real nav — NOT a stub snackbar).
     * Settings is a direct icon button in the profile top bar
     * (`contentDescription = profile_action_settings`), not an entry inside a
     * "More options" overflow menu. The test asserts on the captured event and
     * explicitly verifies that no "Coming soon" snackbar appeared, distinguishing
     * this path from the Edit stub path tested in
     * [editTap_surfacesComingSoonSnackbar].
     */
    @Test
    fun ownProfile_settingsButton_emitsSettingsTapped() {
        val context = composeTestRule.activity
        val settingsLabel = context.getString(R.string.profile_action_settings)
        // SettingsTapped is real nav, NOT a stub — assert the only "coming soon"
        // snackbar that could plausibly fire from this surface (Edit, the other
        // own-profile actions-row affordance) is NOT shown. Resolving the
        // exact production copy from resources keeps the assertion precise
        // (no locale brittleness, no over-broad substring match like "soon"
        // that could trip on unrelated UI).
        val editComingSoon = context.getString(R.string.profile_snackbar_edit_coming_soon)
        val capturedEvents = mutableListOf<ProfileEvent>()

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ProfileScreenContent(
                    state = sampleOwnProfileState(),
                    listState = rememberLazyListState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    postCallbacks = PostCallbacks.None,
                    onEvent = { capturedEvents += it },
                    onBack = null,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(settingsLabel).performClick()
        composeTestRule.waitForIdle()

        assertTrue(
            "Settings tap MUST dispatch ProfileEvent.SettingsTapped; captured: $capturedEvents",
            capturedEvents.contains(ProfileEvent.SettingsTapped),
        )
        composeTestRule.onNodeWithText(editComingSoon).assertDoesNotExist()
    }

    @Test
    fun composeFab_onOwnProfile_passesNullMention() {
        val context = composeTestRule.activity
        val composeLabel = context.getString(R.string.profile_compose_new_post)
        var called = false
        var captured: String? = "sentinel"

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ProfileScreenContent(
                    state = sampleOwnProfileState(),
                    listState = rememberLazyListState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    postCallbacks = PostCallbacks.None,
                    onEvent = {},
                    onBack = null,
                    onComposeClick = {
                        called = true
                        captured = it
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(composeLabel).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Compose FAB tap MUST invoke onComposeClick", called)
        assertEquals("Own-profile compose MUST pass a null mention (blank composer)", null, captured)
    }

    @Test
    fun composeFab_onOtherUserProfile_passesTheirHandle() {
        // The compose FAB is shown on every profile (all-profiles behavior);
        // composing from another user's profile pre-mentions them.
        val context = composeTestRule.activity
        val composeLabel = context.getString(R.string.profile_compose_new_post)
        var captured: String? = null

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ProfileScreenContent(
                    state = sampleOtherUserProfileState(),
                    listState = rememberLazyListState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    postCallbacks = PostCallbacks.None,
                    onEvent = {},
                    onBack = { },
                    onComposeClick = { captured = it },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(composeLabel).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(composeLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Compose from another user's profile MUST pass their handle to pre-mention",
            "bob.bsky.social",
            captured,
        )
    }

    @Test
    fun composeFab_otherUserProfile_headerNotLoaded_fallsBackToRouteHandle() {
        // Header still loading → fall back to the route handle so the mention
        // prefills immediately (as long as it's a handle, not a DID).
        val context = composeTestRule.activity
        val composeLabel = context.getString(R.string.profile_compose_new_post)
        var captured: String? = null

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ProfileScreenContent(
                    state = sampleOtherUserProfileState().copy(header = null),
                    listState = rememberLazyListState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    postCallbacks = PostCallbacks.None,
                    onEvent = {},
                    onBack = { },
                    onComposeClick = { captured = it },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(composeLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "With no header, the FAB MUST fall back to the (non-DID) route handle",
            "bob.bsky.social",
            captured,
        )
    }

    private fun sampleOtherUserProfileState(): ProfileScreenViewState {
        val base = sampleOwnProfileState()
        return base.copy(
            handle = "bob.bsky.social",
            header =
                base.header?.copy(
                    did = "did:plc:bob",
                    handle = "bob.bsky.social",
                    displayName = "Bob",
                ),
            ownProfile = false,
            viewerRelationship = ViewerRelationship.NotFollowing(),
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
