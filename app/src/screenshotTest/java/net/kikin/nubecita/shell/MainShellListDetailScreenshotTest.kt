package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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

/**
 * Screenshot baselines for the inner-pane behavior of `MainShell`'s
 * `NavDisplay` + [`ListDetailSceneStrategy`] across window-size classes.
 *
 * Three baselines, one per `WindowWidthSizeClass` threshold matching
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
            layoutType = NavigationSuiteType.NavigationBar,
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
            directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfo()),
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
