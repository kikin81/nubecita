package net.kikin.nubecita.feature.videoplayer.impl.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerEvent
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerLoadStatus
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Hardware keyboard keys on the fullscreen player (nubecita-h6kf.5): the player takes focus
 * when it opens, Space plays / pauses (not the root clickable's chrome toggle), the arrows
 * skip and M mutes. Without a keyboard none of it is set up.
 */
@OptIn(ExperimentalTestApi::class)
class VideoPlayerKeyboardShortcutsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<VideoPlayerEvent>()

    // A hardware key press takes a real device out of touch mode; the test activity starts in
    // it. setInTouchMode is device-wide, so put it back afterwards.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After
    fun restoreTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    @Test
    fun keys_drivePlayback_whenAKeyboardIsAttached() {
        setPlayer(keyboardShortcuts = true)
        composeTestRule.onRoot().performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
            pressKey(Key.M)
        }
        assertEquals(
            listOf(
                VideoPlayerEvent.PlayPauseClicked,
                VideoPlayerEvent.SkipBack,
                VideoPlayerEvent.SkipForward,
                VideoPlayerEvent.MuteClicked,
            ),
            events,
        )
    }

    @Test
    fun keys_doNothing_withoutAKeyboard() {
        setPlayer(keyboardShortcuts = false)
        composeTestRule.onRoot().performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(emptyList<VideoPlayerEvent>(), events.filter { it == VideoPlayerEvent.PlayPauseClicked })
    }

    private fun setPlayer(keyboardShortcuts: Boolean) {
        composeTestRule.setContent {
            NubecitaTheme {
                VideoPlayerContent(
                    state =
                        VideoPlayerState(
                            loadStatus = VideoPlayerLoadStatus.Ready,
                            aspectRatio = 16f / 9f,
                            isPlaying = true,
                            positionMs = 5_000L,
                            durationMs = 30_000L,
                            chromeVisible = false,
                        ),
                    player = null,
                    onEvent = { events += it },
                    keyboardShortcuts = keyboardShortcuts,
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
