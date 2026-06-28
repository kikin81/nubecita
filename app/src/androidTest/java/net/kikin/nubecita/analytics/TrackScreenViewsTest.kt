package net.kikin.nubecita.analytics

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.Splash
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.profile.api.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behavioral test for [TrackScreenViews]'s de-dupe. The de-dupe is the
 * `LaunchedEffect(topRoute)` keying, which only a real composition exercises
 * — the pure `NavKey → AnalyticsScreen` mapping is covered by the JVM
 * `NavKeyAnalyticsTest`.
 *
 * Instrumented (needs the `run-instrumented` PR label to run in CI). Uses a
 * `createComposeRule` + a recording fake [AnalyticsClient] — no Hilt graph,
 * mirroring `MainShellPersistenceTest`.
 */
@RunWith(AndroidJUnit4::class)
class TrackScreenViewsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun distinctDestinationsOfSameScreenKind_eachLogAView() {
        val analytics = RecordingAnalyticsClient()
        var topRoute by mutableStateOf<NavKey?>(Profile(handle = "alice.bsky.social"))

        composeTestRule.setContent {
            TrackScreenViews(topRoute = topRoute, analytics = analytics)
        }
        composeTestRule.waitForIdle()

        // Navigate to a different profile instance: same screen kind, new
        // NavKey value → a second distinct view.
        composeTestRule.runOnIdle { topRoute = Profile(handle = "bob.bsky.social") }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(AnalyticsScreen.Profile, AnalyticsScreen.Profile),
                analytics.screens,
            )
        }
    }

    @Test
    fun recompositionWithUnchangedRoute_logsOnce() {
        val analytics = RecordingAnalyticsClient()
        val topRoute = Feed
        var tick by mutableIntStateOf(0)

        composeTestRule.setContent {
            // Reading `tick` here forces this content to recompose (and with
            // it the non-skippable TrackScreenViews) without changing the
            // route — the LaunchedEffect must NOT restart.
            Text("tick=$tick")
            TrackScreenViews(topRoute = topRoute, analytics = analytics)
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { tick++ }
        composeTestRule.runOnIdle { tick++ }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            assertEquals(listOf(AnalyticsScreen.Feed), analytics.screens)
        }
    }

    @Test
    fun untrackedRoute_logsNothing_thenTrackedRouteLogsOnce() {
        val analytics = RecordingAnalyticsClient()
        var topRoute by mutableStateOf<NavKey?>(Splash)

        composeTestRule.setContent {
            TrackScreenViews(topRoute = topRoute, analytics = analytics)
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { assertTrue(analytics.screens.isEmpty()) }

        composeTestRule.runOnIdle { topRoute = Feed }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(listOf(AnalyticsScreen.Feed), analytics.screens)
        }
    }
}
