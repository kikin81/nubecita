package net.kikin.nubecita.core.common.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The screen must be held only while a video is genuinely playing, and released
 * the moment it is not — battery is the governing constraint here, so the
 * release path matters more than the hold path.
 */
class KeepScreenOnWhileTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun holdsTheScreenWhileActive() {
        lateinit var probe: android.view.View
        composeTestRule.setContent {
            probe = LocalView.current
            KeepScreenOnWhile(active = true)
        }
        composeTestRule.waitForIdle()

        assertTrue("an active playback must hold the screen", probe.keepScreenOn)
    }

    @Test
    fun doesNotHoldTheScreenWhileInactive() {
        lateinit var probe: android.view.View
        composeTestRule.setContent {
            probe = LocalView.current
            KeepScreenOnWhile(active = false)
        }
        composeTestRule.waitForIdle()

        assertFalse(probe.keepScreenOn)
    }

    /**
     * The one that matters. Pausing must release immediately rather than at the
     * end of the composition's life — otherwise a paused video left on screen
     * keeps the device awake, which is exactly the behaviour being fixed.
     */
    @Test
    fun releasesTheScreenWhenPlaybackStops() {
        lateinit var probe: android.view.View
        var playing by mutableStateOf(true)
        composeTestRule.setContent {
            probe = LocalView.current
            KeepScreenOnWhile(active = playing)
        }
        composeTestRule.waitForIdle()
        assertTrue("precondition: held while playing", probe.keepScreenOn)

        playing = false
        composeTestRule.waitForIdle()

        assertFalse("pausing must release the screen", probe.keepScreenOn)
    }
}
