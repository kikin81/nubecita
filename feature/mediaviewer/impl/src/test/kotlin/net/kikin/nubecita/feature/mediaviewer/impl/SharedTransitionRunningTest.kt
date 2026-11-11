package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.compose.animation.EnterExitState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * When the viewer's image lays out at its fitted rect rather than the whole
 * screen: any time it isn't settled on screen (nubecita-mdmd.2).
 */
internal class SharedTransitionRunningTest {
    @Test
    fun `settled on screen is the only state that uses the whole screen`() {
        assertFalse(isSharedTransitionRunning(EnterExitState.Visible, EnterExitState.Visible))
    }

    @Test
    fun `entering uses the fitted rect`() {
        assertTrue(isSharedTransitionRunning(EnterExitState.PreEnter, EnterExitState.Visible))
    }

    @Test
    fun `leaving uses the fitted rect from its very first frame`() {
        // The frame that starts the exit is where the card's cropped image used
        // to be drawn full-bleed: current is still Visible, target has moved.
        assertTrue(isSharedTransitionRunning(EnterExitState.Visible, EnterExitState.PostExit))
    }
}
