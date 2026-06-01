package net.kikin.nubecita.core.common.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

/**
 * Reactively tracks whether the hosting Activity is in Picture-in-Picture mode,
 * driven by `ComponentActivity.addOnPictureInPictureModeChangedListener`. Used to
 * hide player chrome (and the system bars) and to collapse `MainShell`'s
 * navigation suite while in PiP.
 *
 * Returns a constant `false` when there is no `ComponentActivity` host (previews,
 * screenshot tests) so callers stay inert off-device.
 */
@Composable
public fun rememberIsInPipMode(): State<Boolean> {
    val activity = LocalActivity.current as? ComponentActivity
    // Key on `activity` (like the DisposableEffect below) so the seeded initial
    // value always reflects the current host — not a stale prior Activity.
    val state = remember(activity) { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var isInPip by state

    if (activity != null) {
        DisposableEffect(activity) {
            val listener =
                Consumer<PictureInPictureModeChangedInfo> { info ->
                    isInPip = info.isInPictureInPictureMode
                }
            activity.addOnPictureInPictureModeChangedListener(listener)
            onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
        }
    }
    return state
}
