package net.kikin.nubecita.core.common.haptic

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Per-cell haptic dispatcher for a [PostCard]'s action row.
 *
 * Lives in `:core:common` (not `:designsystem`) because it touches
 * the Android `View` platform API; keeping it out of the design
 * system preserves `:designsystem`'s pure-render contract.
 *
 * Modern haptic constants (`CONFIRM` / `REJECT` / `TOGGLE_*`)
 * landed on API 30 and API 34 respectively. The project's
 * `minSdk = 28` requires a runtime fallback chain — older devices
 * get the closest pre-S equivalent (`LONG_PRESS` or
 * `KEYBOARD_TAP`) so every cell still vibrates in some form.
 *
 * Call from the screen layer's `PostCallbacks` lambdas BEFORE the
 * VM event dispatch so the haptic fires on input, not on the
 * state-flip frame.
 */
@Immutable
public class PostHaptics(
    private val view: View,
) {
    /** Tap enabled the like — confirmation feel. */
    public fun likeOn() {
        perform(modern = HapticFeedbackConstants.CONFIRM, fallback = HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * Tap disabled the like — distinct lighter cue. `TOGGLE_OFF` is API
     * 34+, so pre-U devices fall back to `KEYBOARD_TAP` rather than
     * reusing the heavier `CONFIRM` enable cue (which would make on/off
     * indistinguishable).
     */
    public fun likeOff() {
        performToggleOff()
    }

    /** Tap enabled the repost — confirmation feel, same envelope as [likeOn]. */
    public fun repostOn() {
        perform(modern = HapticFeedbackConstants.CONFIRM, fallback = HapticFeedbackConstants.LONG_PRESS)
    }

    /** Tap disabled the repost — same shape as [likeOff]. */
    public fun repostOff() {
        performToggleOff()
    }

    /**
     * Tap on the reply / share cell — one-shot action. Light cue;
     * universal across SDKs since `KEYBOARD_TAP` is API 1.
     */
    public fun lightTap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * The action the user attempted failed (e.g. toggle-like
     * couldn't commit because the network was down). Fired by the
     * screen's effect collector when it sees the failure path.
     */
    public fun rejected() {
        perform(modern = HapticFeedbackConstants.REJECT, fallback = HapticFeedbackConstants.LONG_PRESS)
    }

    private fun perform(
        modern: Int,
        fallback: Int,
    ) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) modern else fallback
        view.performHapticFeedback(constant)
    }

    private fun performToggleOff() {
        val constant =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.TOGGLE_OFF
            } else {
                // Lighter than CONFIRM so on/off feel distinct on pre-U
                // devices that don't have the dedicated TOGGLE_* constants.
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        view.performHapticFeedback(constant)
    }
}

/**
 * Compose-side handle for [PostHaptics]. Remember-d on
 * `LocalView.current` so the bound `View` follows
 * configuration-change re-attachment.
 */
@Composable
public fun rememberPostHaptics(): PostHaptics {
    val view = LocalView.current
    return remember(view) { PostHaptics(view) }
}
