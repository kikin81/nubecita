package net.kikin.nubecita.core.common.screen

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Outstanding hold requests per window root.
 *
 * `LocalView.current` resolves to the same root view for the whole window, so
 * every [KeepScreenOnWhile] in the tree writes the same flag. Two of them can
 * coexist across a navigation transition — the incoming screen composes before
 * the outgoing one is disposed — and without a count the loser's teardown
 * clears a flag the winner just set, dimming the screen mid-playback. Counting
 * makes the last release, not the last writer, decide.
 *
 * Read and written only from [DisposableEffect] bodies, which run on the main
 * thread, so no synchronization. `WeakHashMap` so a destroyed window's entry
 * cannot pin the view.
 */
private val holdCounts = WeakHashMap<View, Int>()

/**
 * Hold the screen awake while [active], and release it the moment it is not.
 *
 * Set [active] from a signal that means the user is *actually watching* —
 * Media3's `Player.isPlaying` (`playWhenReady && STATE_READY` with no
 * suppression) or a projection of it. Pausing, stalling, reaching the end, or
 * losing audio focus to an incoming call all read false there, so the screen is
 * released without any extra wiring.
 *
 * An inactive instance never touches the flag. It neither holds nor releases,
 * so a paused screen composed alongside a playing one cannot switch the screen
 * off underneath it.
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
        if (active) {
            holdCounts[view] = (holdCounts[view] ?: 0) + 1
            view.keepScreenOn = true
        }
        // Also runs when `active` flips, so a pause releases the screen
        // immediately rather than at the end of the composition's life.
        onDispose {
            if (active) {
                val remaining = ((holdCounts[view] ?: 0) - 1).coerceAtLeast(0)
                if (remaining == 0) {
                    holdCounts.remove(view)
                    view.keepScreenOn = false
                } else {
                    holdCounts[view] = remaining
                }
            }
        }
    }
}
