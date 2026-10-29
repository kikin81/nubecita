package net.kikin.nubecita.core.common.spatial

import androidx.compose.runtime.Composable
import androidx.xr.compose.platform.LocalSpatialCapabilities

/**
 * Encapsulates runtime spatial capability inspection for Android XR environments.
 *
 * Provides a clean abstraction over [LocalSpatialCapabilities], allowing composables
 * to adapt layout and interaction patterns (e.g., floating Orbiters, 56dp gaze targets)
 * when running in spatial sessions without crashing on non-XR devices.
 */
interface SpatialSessionEnvironment {
    val isSpatialUiEnabled: Boolean
    val isContent3dEnabled: Boolean
    val isAppEnvironmentEnabled: Boolean
    val isPassthroughControlEnabled: Boolean
    val isSpatialAudioEnabled: Boolean
}

data class DefaultSpatialSessionEnvironment(
    override val isSpatialUiEnabled: Boolean = false,
    override val isContent3dEnabled: Boolean = false,
    override val isAppEnvironmentEnabled: Boolean = false,
    override val isPassthroughControlEnabled: Boolean = false,
    override val isSpatialAudioEnabled: Boolean = false,
) : SpatialSessionEnvironment

/**
 * Evaluates current spatial capabilities from the ambient [LocalSpatialCapabilities].
 */
@Composable
fun rememberSpatialSessionEnvironment(): SpatialSessionEnvironment {
    val xrCapabilities = LocalSpatialCapabilities.current
    return DefaultSpatialSessionEnvironment(
        isSpatialUiEnabled = xrCapabilities.isSpatialUiEnabled,
        isContent3dEnabled = xrCapabilities.isContent3dEnabled,
        isAppEnvironmentEnabled = xrCapabilities.isAppEnvironmentEnabled,
        isPassthroughControlEnabled = xrCapabilities.isPassthroughControlEnabled,
        isSpatialAudioEnabled = xrCapabilities.isSpatialAudioEnabled,
    )
}
