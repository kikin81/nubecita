package net.kikin.nubecita.core.common.spatial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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
    val isXr: Boolean
    val isSpatialUiEnabled: Boolean
    val isContent3dEnabled: Boolean
    val isAppEnvironmentEnabled: Boolean
    val isPassthroughControlEnabled: Boolean
    val isSpatialAudioEnabled: Boolean
}

data class DefaultSpatialSessionEnvironment(
    override val isXr: Boolean = false,
    override val isSpatialUiEnabled: Boolean = false,
    override val isContent3dEnabled: Boolean = false,
    override val isAppEnvironmentEnabled: Boolean = false,
    override val isPassthroughControlEnabled: Boolean = false,
    override val isSpatialAudioEnabled: Boolean = false,
) : SpatialSessionEnvironment

@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalSpatialSessionEnvironment = compositionLocalOf<SpatialSessionEnvironment?> { null }

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
    val isXr = isXrFeature || xrCapabilities.isSpatialUiEnabled

    return remember(xrCapabilities, isXr) {
        DefaultSpatialSessionEnvironment(
            isXr = isXr,
            isSpatialUiEnabled = xrCapabilities.isSpatialUiEnabled,
            isContent3dEnabled = xrCapabilities.isContent3dEnabled,
            isAppEnvironmentEnabled = xrCapabilities.isAppEnvironmentEnabled,
            isPassthroughControlEnabled = xrCapabilities.isPassthroughControlEnabled,
            isSpatialAudioEnabled = xrCapabilities.isSpatialAudioEnabled,
        )
    }
}
