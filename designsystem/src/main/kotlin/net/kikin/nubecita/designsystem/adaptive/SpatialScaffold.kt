package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * - In Home Space Mode (2D spatial multitasking panel), orbiters are unavailable, so the
 *   same pill capsule sits at the start edge inside the panel.
 *
 * The panel itself is opaque and fills the window: the XR system already rounds and
 * elevates it, and Home Space does not honour a transparent window, so an inset
 * "floating" content card would render as a black frame.
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

    // On XR the content moves between branches at runtime: Compact sub-routes flip
    // the nav type to None, and Home Space <-> Full Space flips isSpatialUiEnabled.
    // Without movableContentOf each flip would dispose and recompose the whole inner
    // NavDisplay. Phones and tablets only ever take the NavigationSuiteScaffold branch.
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { currentContent() } }

    when {
        !spatialEnvironment.isXr -> {
            NavigationSuiteScaffold(
                modifier = modifier,
                navigationSuiteType = navigationSuiteType,
                containerColor = containerColor,
                contentColor = contentColor,
                navigationItems = navigationItems,
                content = movableContent,
            )
        }
        navigationSuiteType == NavigationSuiteType.None -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = containerColor,
                contentColor = contentColor,
                content = movableContent,
            )
        }
        else -> {
            SpatialXrScaffold(
                isSpatialUiEnabled = spatialEnvironment.isSpatialUiEnabled,
                navigationItems = navigationItems,
                containerColor = containerColor,
                contentColor = contentColor,
                modifier = modifier,
                content = movableContent,
            )
        }
    }
}

// `content` is the movableContentOf instance built in SpatialScaffold, so invoking it
// from both branches moves its state across a Home Space <-> Full Space switch.
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        .padding(vertical = 16.dp)
                        .selectableGroup(),
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
            modifier = modifier.fillMaxSize(),
            color = containerColor,
            contentColor = contentColor,
        ) {
            content()
        }
    } else {
        // Home Space: no orbiters, so the capsule sits at the panel's start edge.
        Surface(
            modifier = modifier.fillMaxSize(),
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    navCapsule()
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    content()
                }
            }
        }
    }
}
