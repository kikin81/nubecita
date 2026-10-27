package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.xr.compose.material3.EnableXrComponentOverrides
import androidx.xr.compose.material3.ExperimentalMaterial3XrApi
import net.kikin.nubecita.core.common.spatial.rememberSpatialSessionEnvironment

/**
 * Adaptive scaffold that bridges standard Material 3 [NavigationSuiteScaffold] with
 * Android XR spatial capabilities.
 *
 * When executing in an Android XR session with spatial UI enabled ([isSpatialUiEnabled] == true),
 * wraps the layout in [EnableXrComponentOverrides] so top-level navigation chrome
 * automatically projects into a floating spatial Orbiter anchored to the window panel.
 *
 * On standard devices (phones, foldables, tablets), renders standard [NavigationSuiteScaffold]
 * (NavigationBar on compact widths, NavigationRail on medium/expanded widths).
 */
@OptIn(ExperimentalMaterial3XrApi::class)
@Composable
fun SpatialScaffold(
    navigationSuiteType: NavigationSuiteType,
    navigationItems: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    val spatialEnvironment = rememberSpatialSessionEnvironment()

    if (spatialEnvironment.isSpatialUiEnabled) {
        EnableXrComponentOverrides {
            NavigationSuiteScaffold(
                modifier = modifier,
                navigationSuiteType = navigationSuiteType,
                containerColor = containerColor,
                contentColor = contentColor,
                navigationItems = navigationItems,
                content = content,
            )
        }
    } else {
        NavigationSuiteScaffold(
            modifier = modifier,
            navigationSuiteType = navigationSuiteType,
            containerColor = containerColor,
            contentColor = contentColor,
            navigationItems = navigationItems,
            content = content,
        )
    }
}
