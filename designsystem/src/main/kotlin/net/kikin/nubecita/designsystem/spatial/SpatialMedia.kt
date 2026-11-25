package net.kikin.nubecita.designsystem.spatial

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterDefaults
import androidx.xr.compose.spatial.OrbiterPosition
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.unit.DpVolumeOffset
import androidx.xr.runtime.Session
import androidx.xr.scenecore.SpatialEnvironment
import androidx.xr.scenecore.scene

/**
 * Hosts media controls in an Orbiter floating just below the app's panel, so the video
 * itself stays fully unobscured. Only meaningful in Full Space, where Orbiters exist: call
 * it only when [SpatialSessionEnvironment.isSpatialUiEnabled], and keep the controls in the
 * normal overlay otherwise.
 *
 * The content brings its own background; the Orbiter only gives it a rounded shape.
 */
@Composable
fun BottomControlsOrbiter(content: @Composable () -> Unit) {
    Orbiter(
        position =
            OrbiterPosition.BottomCenter(
                verticalEdgeAlignment = OrbiterPosition.EdgeAlignment.Outside,
                // Positive y is down in Orbiter offsets: a gap below the panel's bottom edge.
                offset = DpVolumeOffset(0.dp, ORBITER_GAP, OrbiterDefaults.Elevation),
            ),
        shape = SpatialRoundedCornerShape(CornerSize(28.dp)),
        content = content,
    )
}

private val ORBITER_GAP = 24.dp

/**
 * Theater Mode for video on Android XR: Full Space with the passthrough faded out, so the
 * room darkens behind the video. Obtain one with [rememberTheaterMode]; it is null wherever
 * there is no XR session (phones, foldables, tablets, previews).
 *
 * Leaving restores what was there before: the passthrough preference is cleared, and Home
 * Space is requested only if Theater Mode is what moved the app into Full Space. Leaving
 * Full Space some other way (the system's own controls) ends Theater Mode too.
 */
@Stable
class TheaterMode internal constructor(
    private val space: XrSpaceControls,
) {
    /** Whether this player turned Theater Mode on (and it has not ended yet). */
    var isActive: Boolean by mutableStateOf(false)
        private set

    private var isFullSpace = false
    private var enteredFullSpace = false

    /** Enters Theater Mode, or leaves it if it is on. */
    fun toggle() {
        if (isActive) exit() else enter()
    }

    private fun enter() {
        enteredFullSpace = !isFullSpace
        if (enteredFullSpace) space.requestFullSpace()
        space.setPassthroughOpacity(THEATER_PASSTHROUGH_OPACITY)
        isActive = true
    }

    internal fun exit() {
        if (!isActive) return
        val returnToHomeSpace = enteredFullSpace && isFullSpace
        clearPassthrough()
        if (returnToHomeSpace) space.requestHomeSpace()
    }

    internal fun onSpaceChanged(isFullSpace: Boolean) {
        val left = this.isFullSpace && !isFullSpace
        this.isFullSpace = isFullSpace
        // Left Full Space by other means while dark: drop the preference, do not fight it.
        if (left && isActive) clearPassthrough()
    }

    private fun clearPassthrough() {
        space.setPassthroughOpacity(null)
        enteredFullSpace = false
        isActive = false
    }
}

/**
 * The [TheaterMode] for this XR session, or null when there is none. Theater Mode ends
 * when the caller leaves composition (the player closes), so the room never stays dark
 * after the video.
 */
@Composable
fun rememberTheaterMode(): TheaterMode? {
    val session = LocalSession.current ?: return null
    val isFullSpace = rememberSpatialSessionEnvironment().isSpatialUiEnabled
    val theaterMode = remember(session) { TheaterMode(SessionSpaceControls(session)) }
    // Not a LaunchedEffect: the space must be known before the first toggle, with no frame
    // of delay in which a tap would read a stale value.
    SideEffect { theaterMode.onSpaceChanged(isFullSpace) }
    DisposableEffect(theaterMode) {
        onDispose { theaterMode.exit() }
    }
    return theaterMode
}

/** The scene calls [TheaterMode] makes; an interface so its state machine is unit-testable. */
internal interface XrSpaceControls {
    fun requestFullSpace()

    fun requestHomeSpace()

    /** The app's passthrough preference, 0 (faded out) to 1; null clears the preference. */
    fun setPassthroughOpacity(opacity: Float?)
}

private class SessionSpaceControls(
    private val session: Session,
) : XrSpaceControls {
    override fun requestFullSpace() = session.scene.requestFullSpace()

    override fun requestHomeSpace() = session.scene.requestHomeSpace()

    override fun setPassthroughOpacity(opacity: Float?) {
        session.scene.spatialEnvironment.preferredPassthroughOpacity =
            opacity ?: SpatialEnvironment.NO_PASSTHROUGH_OPACITY_PREFERENCE
    }
}

/** 0 = passthrough fully faded out: the system's virtual environment behind the video. */
private const val THEATER_PASSTHROUGH_OPACITY = 0f
