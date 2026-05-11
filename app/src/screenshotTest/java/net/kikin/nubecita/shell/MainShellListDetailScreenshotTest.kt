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
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute

/**
 * Screenshot baselines for the inner-pane behavior of `MainShell`'s
 * `NavDisplay` + [`ListDetailSceneStrategy`] across window-size classes.
 *
 * Three baselines, one per `WindowSizeClass` width breakpoint matching
 * `MainShellChromeScreenshotTest`'s constants:
 *
 * - **Compact (360dp):** strategy collapses to single-pane → only the
 *   Feed list pane is composed; no detail placeholder.
 * - **Medium (600dp) / Expanded (840dp):** strategy renders two-pane →
 *   Feed list pane in left pane, detail placeholder in right pane.
 *
 * These baselines are the regression guard against accidental
 * `sceneStrategies =` removal — if `MainShell` ever drops the strategy,
 * the medium/expanded baselines change visibly.
 *
 * The `FeedDetailPlaceholder` Composable in `:feature:feed:impl` is
 * `internal` and unreachable from `:app`'s screenshotTest source set, so
 * the test substitutes a `FakeDetailPlaceholder` of similar shape — its
 * own visual correctness is covered by
 * `FeedDetailPlaceholderScreenshotTest` in `:feature:feed:impl`. What
 * this test asserts is the strategy wiring (compact = no placeholder,
 * medium/expanded = placeholder visible adjacent to list), which is
 * placeholder-content-agnostic.
 *
 * The fake `FakeFeedListContent` similarly stands in for the real
 * `FeedScreen`, which would require a Hilt graph and ATProto wiring to
 * compose. Strategy + metadata wiring is what's under test here.
 */

private const val COMPACT_WIDTH_DP: Int = 360
private const val MEDIUM_WIDTH_DP: Int = 600
private const val EXPANDED_WIDTH_DP: Int = 840

@PreviewTest
@Preview(name = "list-detail-compact", widthDp = COMPACT_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellListDetailCompact() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            FakeListDetailNavDisplay()
        }
    }
}

@PreviewTest
@Preview(name = "list-detail-medium", widthDp = MEDIUM_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellListDetailMedium() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            FakeListDetailNavDisplay()
        }
    }
}

@PreviewTest
@Preview(name = "list-detail-expanded", widthDp = EXPANDED_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellListDetailExpanded() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            FakeListDetailNavDisplay()
        }
    }
}

@PreviewTest
@Preview(name = "list-detail-medium-with-detail", widthDp = MEDIUM_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellListDetailMediumWithDetail() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            FakeListDetailNavDisplayWithDetail()
        }
    }
}

@PreviewTest
@Preview(name = "list-detail-expanded-with-detail", widthDp = EXPANDED_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellListDetailExpandedWithDetail() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            FakeListDetailNavDisplayWithDetail()
        }
    }
}

/**
 * Renders the inner content with the same wiring as production
 * `MainShell.NavDisplay`: the real [`ListDetailSceneStrategy`] driving a
 * back stack of `[Feed]` whose entry carries real `listPane{}` metadata.
 * Substitutes fakes only for the per-pane content, since the real
 * `FeedScreen` and `FeedDetailPlaceholder` belong to other modules and
 * their visual correctness is covered by tests in those modules.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun FakeListDetailNavDisplay() {
    val backStack: SnapshotStateList<NavKey> =
        remember { mutableStateListOf<NavKey>(Feed) }
    val sceneStrategy =
        rememberListDetailSceneStrategy<NavKey>(
            directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfoV2()),
        )

    val fakeFeedInstaller: EntryProviderInstaller = {
        entry<Feed>(
            metadata =
                ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { FakeDetailPlaceholder() },
                ),
        ) {
            FakeFeedListContent()
        }
    }

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
            },
    )
}

/**
 * Renders the inner content with a back stack of `[Feed, PostDetailRoute]`
 * and a real [`ListDetailSceneStrategy`] driving both entries. The Feed
 * entry carries the same `listPane{}` metadata the production wiring
 * uses; the PostDetail entry carries `detailPane()` metadata — the
 * literal under test by this fixture.
 *
 * Substitutes `FakePostDetailContent` for the real `PostDetailScreen`
 * for the same reason `FakeFeedListContent` substitutes for the real
 * `FeedScreen`: composing the production screen would require a Hilt
 * graph and ATProto wiring. Strategy + metadata pairing is the
 * contract under visual test here.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun FakeListDetailNavDisplayWithDetail() {
    val detailRoute =
        PostDetailRoute(postUri = "at://did:plc:fake/app.bsky.feed.post/abc123")
    val backStack: SnapshotStateList<NavKey> =
        remember { mutableStateListOf<NavKey>(Feed, detailRoute) }
    val sceneStrategy =
        rememberListDetailSceneStrategy<NavKey>(
            directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfoV2()),
        )

    val fakeFeedInstaller: EntryProviderInstaller = {
        entry<Feed>(
            metadata =
                ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { FakeDetailPlaceholder() },
                ),
        ) {
            FakeFeedListContent()
        }
    }

    val fakePostDetailInstaller: EntryProviderInstaller = {
        entry<PostDetailRoute>(
            metadata = ListDetailSceneStrategy.detailPane(),
        ) {
            FakePostDetailContent()
        }
    }

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

@Composable
private fun FakeFeedListContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) { index ->
            Text(
                text = "Post #${index + 1}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun FakeDetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "DETAIL_PANE",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FakePostDetailContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Post detail (fake)",
            style = MaterialTheme.typography.titleMedium,
        )
        repeat(3) { index ->
            Text(
                text = "Reply #${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
