package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute

private const val SPATIAL_PANEL_WIDTH_DP: Int = 1024
private const val SPATIAL_PANEL_HEIGHT_DP: Int = 768

@PreviewTest
@Preview(
    name = "spatial-list-detail-feed-with-detail",
    widthDp = SPATIAL_PANEL_WIDTH_DP,
    heightDp = SPATIAL_PANEL_HEIGHT_DP,
)
@Composable
private fun SpatialListDetailFeedWithDetailPreview() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            SpatialFakeListDetailNavDisplayWithDetail()
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun SpatialFakeListDetailNavDisplayWithDetail() {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = calculateListDetailDirective(adaptiveInfo, isSpatialUiEnabled = true)
    val sceneStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val backStack =
        remember {
            mutableStateListOf<NavKey>(
                Feed,
                PostDetailRoute(postUri = "at://did:plc:fake/app.bsky.feed.post/spatial123"),
            )
        }

    val fakeFeedInstaller: EntryProviderInstaller = {
        entry<Feed>(
            metadata =
                ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { SpatialFakeDetailPlaceholder() },
                ),
        ) {
            SpatialFakeFeedListContent()
        }
    }

    val fakePostDetailInstaller: EntryProviderInstaller = {
        entry<PostDetailRoute>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) {
            SpatialFakePostDetailContent()
        }
    }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) },
            sceneStrategies = listOf(sceneStrategy),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    fakeFeedInstaller()
                    fakePostDetailInstaller()
                },
        )
    }
}

@Composable
private fun SpatialFakeFeedListContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Spatial Feed List",
            style = MaterialTheme.typography.titleLarge,
        )
        repeat(4) { index ->
            Text(
                text = "Spatial Post Item #${index + 1}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SpatialFakeDetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SPATIAL_DETAIL_PANE",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpatialFakePostDetailContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Spatial Post Thread Detail",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Full conversation thread rendered alongside feed in 3D space.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
