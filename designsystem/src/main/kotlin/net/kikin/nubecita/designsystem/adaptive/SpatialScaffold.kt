package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterAlignment
import androidx.xr.compose.spatial.OrbiterDefaults
import androidx.xr.compose.spatial.OrbiterEdgeOffsetType
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.unit.DpVolumeOffset
import net.kikin.nubecita.core.common.spatial.rememberSpatialSessionEnvironment

/**
 * Adaptive scaffold that bridges standard Material 3 [NavigationSuiteScaffold] with
 * Android XR spatial capabilities.
 *
 * When executing in an Android XR session:
 * - If spatial UI is enabled (Full Space Mode), anchors top-level navigation into
 *   a 3D [Orbiter] floating outside the panel.
 * - In Home Space Mode (2D spatial multitasking panel), renders the navigation items
 *   as a detached floating pill capsule anchored to the side of the main window panel,
 *   separated by empty space from the elevated rounded content panel (matching the Google
 *   Play Store XR layout).
 *
 * On standard devices (phones, foldables, tablets), renders standard [NavigationSuiteScaffold]
 * (NavigationBar on compact widths, NavigationRail on medium/expanded widths).
 */
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

    when {
        spatialEnvironment.isXr && navigationSuiteType != NavigationSuiteType.None -> {
            SpatialXrScaffold(
                isSpatialUiEnabled = spatialEnvironment.isSpatialUiEnabled,
                navigationItems = navigationItems,
                containerColor = containerColor,
                contentColor = contentColor,
                modifier = modifier,
                content = content,
            )
        }
        spatialEnvironment.isXr && navigationSuiteType == NavigationSuiteType.None -> {
            Surface(
                modifier = modifier.fillMaxSize().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 4.dp,
            ) {
                content()
            }
        }
        else -> {
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
}

@Suppress("ktlint:compose:content-slot-reused")
@Composable
private fun SpatialXrScaffold(
    isSpatialUiEnabled: Boolean,
    navigationItems: @Composable () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val navCapsule: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            border =
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
            modifier = Modifier.wrapContentHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(80.dp)
                        .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                navigationItems()
            }
        }
    }

    if (isSpatialUiEnabled) {
        // OuterEdge places the capsule fully outside the panel; the user offset is
        // absolute (not mirrored for RTL), so push it away from the start edge by hand.
        // The deprecated ContentEdge/OrbiterOffsetType overload measured `offset` to the
        // orbiter's FAR edge, which pulled the 80dp capsule ~56dp into the feed.
        val gap = if (LocalLayoutDirection.current == LayoutDirection.Ltr) (-24).dp else 24.dp
        Orbiter(
            alignment =
                OrbiterAlignment.CenterStart(
                    edgeOffsetType = OrbiterEdgeOffsetType.OuterEdge,
                    offset = DpVolumeOffset(gap, 0.dp, OrbiterDefaults.Elevation),
                ),
            shape = SpatialRoundedCornerShape(CornerSize(32.dp)),
        ) {
            navCapsule()
        }
        Surface(
            modifier = modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = 4.dp,
        ) {
            content()
        }
    } else {
        // Home Space Mode: Detached floating capsule beside rounded main content panel
        Row(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navCapsule()

            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 4.dp,
            ) {
                content()
            }
        }
    }
}
