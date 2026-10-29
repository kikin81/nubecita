package net.kikin.nubecita.core.common.spatial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.xr.compose.platform.LocalSpatialCapabilities

/**
 * Encapsulates runtime spatial capability inspection for Android XR environments.
 *
 * Provides a clean abstraction over [LocalSpatialCapabilities], allowing composables
 * to adapt layout and interaction patterns (e.g., floating Orbiters, 56dp gaze targets)
 * when running in spatial sessions without crashing on non-XR devices.
 */
interface SpatialSessionEnvironment {
    /** Running on an Android XR device, in Home Space or Full Space. */
    val isXr: Boolean

    /** Full Space: spatial UI such as Orbiters is available. */
    val isSpatialUiEnabled: Boolean
}

data class DefaultSpatialSessionEnvironment(
    override val isXr: Boolean = false,
    override val isSpatialUiEnabled: Boolean = false,
) : SpatialSessionEnvironment

/**
 * Overrides the detected environment. Provided only by previews and screenshot tests,
 * which never change it at runtime, hence static.
 */
val LocalSpatialSessionEnvironment = staticCompositionLocalOf<SpatialSessionEnvironment?> { null }

/**
 * Evaluates current spatial capabilities from [LocalSpatialSessionEnvironment] if provided,
 * or ambient [LocalSpatialCapabilities] and system features.
 */
@Composable
fun rememberSpatialSessionEnvironment(): SpatialSessionEnvironment {
    val provided = LocalSpatialSessionEnvironment.current
    if (provided != null) return provided

    val context = LocalContext.current
    val xrCapabilities = LocalSpatialCapabilities.current
    val isXrFeature =
        remember(context) {
            runCatching {
                context.packageManager.hasSystemFeature("android.software.xr.api.spatial")
            }.getOrDefault(false)
        }
    val isSpatialUiEnabled = xrCapabilities.isSpatialUiEnabled
    val isXr = isXrFeature || isSpatialUiEnabled

    return remember(isXr, isSpatialUiEnabled) {
        DefaultSpatialSessionEnvironment(isXr = isXr, isSpatialUiEnabled = isSpatialUiEnabled)
    }
}
