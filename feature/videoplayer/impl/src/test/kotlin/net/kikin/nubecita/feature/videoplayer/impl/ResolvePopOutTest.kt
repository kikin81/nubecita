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
    fun `enters PiP when enabled (Pro), does not route to paywall`() {
        var enteredPip = false
        var routedToPaywall = false

        resolvePopOut(
            pipEnabled = true,
            enterPip = { enteredPip = true },
            navigateToPaywall = { routedToPaywall = true },
        )

        assertTrue(enteredPip)
        assertFalse(routedToPaywall)
    }

    @Test
    fun `routes to paywall when not enabled (non-Pro), does not enter PiP`() {
        var enteredPip = false
        var routedToPaywall = false

        resolvePopOut(
            pipEnabled = false,
            enterPip = { enteredPip = true },
            navigateToPaywall = { routedToPaywall = true },
        )

        assertFalse(enteredPip)
        assertTrue(routedToPaywall)
    }
}
