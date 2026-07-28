package net.kikin.nubecita.core.common.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Hold the screen awake while [active].
 *
 * Media3 has no API for this — `media3-ui-compose`'s `PlayerSurface` is a bare
 * surface wrapper, and the classic `PlayerView` is not a dependency here. Note
 * this is *not* `ExoPlayer.setWakeMode`, which keeps the CPU and network alive
 * for background playback: that costs battery and does not stop the display
 * dimming.
 *
 * Pass Media3's `isPlaying` (`playWhenReady && STATE_READY`, no suppression) so
 * pause, stall, end, and audio-focus loss all release the screen for free. Do
 * not pass `true` for buffering or for muted feed previews — either would keep
 * the screen awake while the user is not watching.
 *
 * Sets `View.keepScreenOn` rather than `FLAG_KEEP_SCREEN_ON` on the window: the
 * view-scoped flag is cleared by the framework on detach, so a crash or an
 * uncovered navigation path cannot leave the screen stuck on.
 *
 * `LocalView.current` is the window root, shared by every caller, so this
 * assumes **at most one caller is active at a time**. That holds because only
 * one video surface plays at once — the vertical feed releases the shared
 * player on entry, and neither video route navigates to the other. If a second
 * concurrently-playing surface ever appears, the two will fight over the flag
 * and this needs a hold count.
 */
@Composable
fun KeepScreenOnWhile(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}
