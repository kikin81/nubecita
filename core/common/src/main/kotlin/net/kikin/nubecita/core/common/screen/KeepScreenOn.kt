package net.kikin.nubecita.core.common.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Hold the screen awake while [active], and release it the moment it is not.
 *
 * Set [active] from a signal that means the user is *actually watching* —
 * Media3's `Player.isPlaying` (`playWhenReady && STATE_READY` with no
 * suppression) or a projection of it. Pausing, stalling, reaching the end, or
 * losing audio focus to an incoming call all read false there, so the screen is
 * released without any extra wiring.
 *
 * ## Why the view flag, not the window flag
 *
 * This sets `View.keepScreenOn` rather than
 * `Window.addFlags(FLAG_KEEP_SCREEN_ON)`. Both reach the same window flag, but
 * the view-scoped one is cleared by the framework when the view detaches. That
 * makes stranding the screen awake — a crash, a process death, a navigation
 * path nobody thought to cover — impossible rather than merely unlikely. The
 * window flag persists until something explicitly clears it, and one missed
 * path means a phone that never sleeps.
 *
 * ## What this is not
 *
 * Not a wake lock, and deliberately not `ExoPlayer.setWakeMode`. That keeps the
 * **CPU and network** alive for *background* playback — it costs battery and
 * would not stop the display dimming, which is the actual problem here.
 *
 * @param active `true` only while playback is genuinely running. Do not pass
 *   `true` for buffering (a stall on a dead network would hold the screen
 *   indefinitely) or for muted autoplay previews in a scrolling feed (the
 *   screen would never sleep while the user browses).
 */
@Composable
fun KeepScreenOnWhile(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        // Also runs when `active` flips, so a pause releases the screen
        // immediately rather than at the end of the composition's life.
        onDispose { view.keepScreenOn = false }
    }
}
