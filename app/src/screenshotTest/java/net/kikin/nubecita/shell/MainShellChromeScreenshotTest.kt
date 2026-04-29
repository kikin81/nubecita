package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [MainShellChrome] covering:
 *
 * - The chrome swap across `WindowWidthSizeClass` breakpoints
 *   (`NavigationBar` at compact, `NavigationRail` at medium and expanded).
 * - The selected-state indicator on each of the four top-level
 *   destinations at compact width (the most common form factor).
 *
 * Seven baselines total. Each `@PreviewTest`-annotated function also
 * doubles as a Compose `@Preview` for in-IDE inspection. The chrome
 * composable is `internal`, so these tests live in `:app`'s
 * `screenshotTest` source set.
 *
 * The content area renders a fixed `Text("…")` placeholder rather than
 * the production inner `NavDisplay` — the goal here is to baseline the
 * chrome itself; tab navigation behavior is exercised by the
 * `MainShellNavStateTest` unit tests in `:core:common` and the
 * instrumented persistence test in this module.
 */

private const val COMPACT_WIDTH_DP: Int = 360
private const val MEDIUM_WIDTH_DP: Int = 600
private const val EXPANDED_WIDTH_DP: Int = 840

@PreviewTest
@Preview(name = "compact-bar-feed-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarFeedSelected() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationBar,
        ) {
            ChromeContentPlaceholder(label = "Feed")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-search-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarSearchSelected() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[1].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationBar,
        ) {
            ChromeContentPlaceholder(label = "Search")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-chats-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarChatsSelected() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[2].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationBar,
        ) {
            ChromeContentPlaceholder(label = "Chats")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-you-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarYouSelected() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[3].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationBar,
        ) {
            ChromeContentPlaceholder(label = "You")
        }
    }
}

@PreviewTest
@Preview(name = "medium-rail-feed-selected", widthDp = MEDIUM_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellChromeMediumRailFeedSelected() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            ChromeContentPlaceholder(label = "Feed (medium / rail)")
        }
    }
}

@PreviewTest
@Preview(name = "expanded-rail-feed-selected", widthDp = EXPANDED_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellChromeExpandedRailFeedSelected() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            ChromeContentPlaceholder(label = "Feed (expanded / rail)")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-feed-selected-dark", widthDp = COMPACT_WIDTH_DP, heightDp = 640, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainShellChromeCompactBarFeedSelectedDark() {
    NubecitaTheme(dynamicColor = false) {
        MainShellChrome(
            activeKey = TopLevelDestinations[0].key,
            onTabClick = {},
            layoutType = NavigationSuiteType.NavigationBar,
        ) {
            ChromeContentPlaceholder(label = "Feed (dark)")
        }
    }
}

@Composable
private fun ChromeContentPlaceholder(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}
