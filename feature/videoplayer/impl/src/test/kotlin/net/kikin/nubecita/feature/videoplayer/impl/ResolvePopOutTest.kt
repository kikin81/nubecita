package net.kikin.nubecita.feature.videoplayer.impl

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pop-out routing contract (nubecita-q5ge.8): a Pro user (PiP enabled)
 * enters Picture-in-Picture; everyone else is routed to the paywall. The
 * decision is a pure function ([resolvePopOut]) so it's testable without an
 * Activity / PiP harness — design D5 keeps it in the Compose layer, not the VM.
 */
internal class ResolvePopOutTest {
    @Test
    fun `enters PiP when enabled`() {
        var enteredPip = false

        resolvePopOut(
            pipEnabled = true,
            enterPip = { enteredPip = true },
        )

        assertTrue(enteredPip)
    }

    @Test
    fun `does not enter PiP when disabled`() {
        var enteredPip = false

        resolvePopOut(
            pipEnabled = false,
            enterPip = { enteredPip = true },
        )

        assertFalse(enteredPip)
    }
}
