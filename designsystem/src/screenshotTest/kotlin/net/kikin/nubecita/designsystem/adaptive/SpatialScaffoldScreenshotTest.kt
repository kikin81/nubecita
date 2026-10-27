package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.common.spatial.DefaultSpatialSessionEnvironment
import net.kikin.nubecita.core.common.spatial.LocalSpatialSessionEnvironment
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private const val COMPACT_WIDTH_DP: Int = 360
private const val MEDIUM_WIDTH_DP: Int = 600
private const val XR_WIDTH_DP: Int = 1000
private const val HEIGHT_DP: Int = 640

@PreviewTest
@Preview(name = "spatial-scaffold-compact", widthDp = COMPACT_WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun SpatialScaffoldCompactPreview() {
    NubecitaCanvasPreviewTheme {
        SpatialScaffold(
            navigationSuiteType = NavigationSuiteType.ShortNavigationBarCompact,
            navigationItems = {
                NavigationSuiteItem(
                    navigationSuiteType = NavigationSuiteType.ShortNavigationBarCompact,
                    selected = true,
                    onClick = {},
                    icon = { NubecitaIcon(name = NubecitaIconName.Home, contentDescription = "Home") },
                    label = null,
                )
                NavigationSuiteItem(
                    navigationSuiteType = NavigationSuiteType.ShortNavigationBarCompact,
                    selected = false,
                    onClick = {},
                    icon = { NubecitaIcon(name = NubecitaIconName.Search, contentDescription = "Search") },
                    label = null,
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Spatial Scaffold Content")
            }
        }
    }
}

@PreviewTest
@Preview(name = "spatial-scaffold-medium-rail", widthDp = MEDIUM_WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun SpatialScaffoldMediumRailPreview() {
    NubecitaCanvasPreviewTheme {
        SpatialScaffold(
            navigationSuiteType = NavigationSuiteType.NavigationRail,
            navigationItems = {
                NavigationSuiteItem(
                    navigationSuiteType = NavigationSuiteType.NavigationRail,
                    selected = true,
                    onClick = {},
                    icon = { NubecitaIcon(name = NubecitaIconName.Home, contentDescription = "Home") },
                    label = null,
                )
                NavigationSuiteItem(
                    navigationSuiteType = NavigationSuiteType.NavigationRail,
                    selected = false,
                    onClick = {},
                    icon = { NubecitaIcon(name = NubecitaIconName.Search, contentDescription = "Search") },
                    label = null,
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Spatial Scaffold Content")
            }
        }
    }
}

@PreviewTest
@Preview(name = "spatial-scaffold-xr-floating-orbiter", widthDp = XR_WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun SpatialScaffoldXrFloatingOrbiterPreview() {
    CompositionLocalProvider(
        LocalSpatialSessionEnvironment provides DefaultSpatialSessionEnvironment(isXr = true),
    ) {
        NubecitaCanvasPreviewTheme {
            SpatialScaffold(
                navigationSuiteType = NavigationSuiteType.NavigationRail,
                navigationItems = {
                    NavigationSuiteItem(
                        navigationSuiteType = NavigationSuiteType.NavigationRail,
                        selected = true,
                        onClick = {},
                        icon = { NubecitaIcon(name = NubecitaIconName.Home, contentDescription = "Home") },
                        label = null,
                    )
                    NavigationSuiteItem(
                        navigationSuiteType = NavigationSuiteType.NavigationRail,
                        selected = false,
                        onClick = {},
                        icon = { NubecitaIcon(name = NubecitaIconName.Search, contentDescription = "Search") },
                        label = null,
                    )
                },
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Spatial Scaffold Content")
                }
            }
        }
    }
}
