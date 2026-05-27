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
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search

/**
 * Screenshot baselines for [MainShellChrome] covering:
 *
 * - The chrome swap across `WindowSizeClass` width breakpoints
 *   (`NavigationBar` at compact, `NavigationRail` at medium and expanded).
 * - The selected-state indicator on each of the five top-level
 *   destinations at compact width (the most common form factor).
 * - The Notifications-tab `BadgedBox` overlay across the rendering
 *   thresholds (no-badge, single-digit, double-digit, "99+" overflow).
 *
 * Eleven baselines total. Each `@PreviewTest`-annotated function also
 * doubles as a Compose `@Preview` for in-IDE inspection. The chrome
 * composable is `internal`, so these tests live in `:app`'s
 * `screenshotTest` source set.
 *
 * The content area renders a fixed `Text("…")` placeholder rather than
 * the production inner `NavDisplay` — the goal here is to baseline the
 * chrome itself; tab navigation behavior is exercised by the
 * `MainShellNavStateTest` unit tests in `:core:common` and the
 * instrumented persistence test in this module.
 *
 * Selected-tab previews reference the destination `NavKey` directly
 * (e.g. `activeKey = NotificationsTab`) rather than indexing into
 * `TopLevelDestinations` — index references would shift silently if
 * the destination order ever changes.
 */

private const val COMPACT_WIDTH_DP: Int = 360
private const val MEDIUM_WIDTH_DP: Int = 600
private const val EXPANDED_WIDTH_DP: Int = 840

@PreviewTest
@Preview(name = "compact-bar-feed-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarFeedSelected() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Feed")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-search-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarSearchSelected() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Search,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Search")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-notifications-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarNotificationsSelected() {
    // Selected with no badge — confirms the filled bell renders without
    // a stale-badge overlay.
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = NotificationsTab,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Notifications")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-chats-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarChatsSelected() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Chats,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Chats")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-you-selected", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarYouSelected() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Profile(handle = null),
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "You")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-badge-1", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarBadgeOne() {
    // Single-digit badge; user on Feed (Notifications NOT selected) so the
    // BadgedBox is rendered over the unfilled bell.
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 1,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Feed (1 unread)")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-badge-9", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarBadgeNine() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 9,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Feed (9 unread)")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-badge-overflow", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarBadgeOverflow() {
    // Count > 99 — must render "99+" per the formatUnreadCount() cap.
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 137,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Feed (137 unread → 99+)")
        }
    }
}

// ----- Threshold sweep: filled bell + badge ------------------------------
//
// Notifications tab IS the active destination — so the bell renders FILLED
// (the FILL axis of the variable Material Symbols font flips to the
// activity-dot variant) AND the badge overlays it when the count > 0.
// This is the production state when a push arrives while the user is
// sitting on the Notifications tab, before the mark-seen-on-tab-exit
// handshake fires. The earlier badge previews use Feed-selected, so the
// outlined bell + badge case is covered; these three fill in the filled
// bell + badge case at each digit-count threshold.

@PreviewTest
@Preview(name = "compact-bar-notifications-active-no-badge", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarNotificationsActiveNoBadge() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = NotificationsTab,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Notifications (0 unread)")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-notifications-active-badge-1", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarNotificationsActiveBadgeOne() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = NotificationsTab,
            notificationsUnreadCount = 1,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Notifications (1 unread)")
        }
    }
}

@PreviewTest
@Preview(name = "compact-bar-notifications-active-badge-overflow", widthDp = COMPACT_WIDTH_DP, heightDp = 640)
@Composable
private fun MainShellChromeCompactBarNotificationsActiveBadgeOverflow() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = NotificationsTab,
            notificationsUnreadCount = 137,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Notifications (137 unread → 99+)")
        }
    }
}

@PreviewTest
@Preview(name = "medium-rail-feed-selected", widthDp = MEDIUM_WIDTH_DP, heightDp = 800)
@Composable
private fun MainShellChromeMediumRailFeedSelected() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 0,
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
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 0,
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
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 0,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Feed (dark)")
        }
    }
}

@PreviewTest
@Preview(
    name = "compact-bar-badge-overflow-dark",
    widthDp = COMPACT_WIDTH_DP,
    heightDp = 640,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MainShellChromeCompactBarBadgeOverflowDark() {
    NubecitaCanvasPreviewTheme {
        MainShellChrome(
            activeKey = Feed,
            notificationsUnreadCount = 137,
            onTabClick = {},
            layoutType = NavigationSuiteType.ShortNavigationBarCompact,
        ) {
            ChromeContentPlaceholder(label = "Feed (99+ unread, dark)")
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
