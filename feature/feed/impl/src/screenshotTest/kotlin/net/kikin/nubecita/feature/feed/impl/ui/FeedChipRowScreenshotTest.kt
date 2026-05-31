package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.feed.impl.FeedHostStatus

@PreviewTest
@Preview(name = "loading", showBackground = true)
@Composable
private fun FeedChipRowLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        FeedChipRow(
            feedChips = persistentListOf(),
            pinnedLists = persistentListOf(),
            selectedFeedUri = null,
            status = FeedHostStatus.Loading,
            onSelectFeed = {},
            onSelectList = {},
            onRetry = {},
            onManageFeedsClick = {},
            onOpenListsSheet = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-following-selected", showBackground = true)
@Composable
private fun FeedChipRowReadyFollowingSelectedScreenshot() {
    val chips =
        persistentListOf(
            PinnedFeedUi("1", "following-uri", FeedKind.Following, "Following", null),
            PinnedFeedUi("2", "generator-discover-uri", FeedKind.Generator, "Discover", null),
            PinnedFeedUi("3", "generator-art-uri", FeedKind.Generator, "Art", "avatar-url-mock"),
        )
    NubecitaCanvasPreviewTheme {
        FeedChipRow(
            feedChips = chips,
            pinnedLists = persistentListOf(),
            selectedFeedUri = "following-uri",
            status = FeedHostStatus.Ready,
            onSelectFeed = {},
            onSelectList = {},
            onRetry = {},
            onManageFeedsClick = {},
            onOpenListsSheet = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-generator-no-avatar-selected", showBackground = true)
@Composable
private fun FeedChipRowReadyGeneratorNoAvatarSelectedScreenshot() {
    val chips =
        persistentListOf(
            PinnedFeedUi("1", "following-uri", FeedKind.Following, "Following", null),
            PinnedFeedUi("2", "generator-discover-uri", FeedKind.Generator, "Discover", null),
            PinnedFeedUi("3", "generator-art-uri", FeedKind.Generator, "Art", "avatar-url-mock"),
        )
    NubecitaCanvasPreviewTheme {
        FeedChipRow(
            feedChips = chips,
            pinnedLists = persistentListOf(),
            selectedFeedUri = "generator-discover-uri",
            status = FeedHostStatus.Ready,
            onSelectFeed = {},
            onSelectList = {},
            onRetry = {},
            onManageFeedsClick = {},
            onOpenListsSheet = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-generator-with-avatar-selected", showBackground = true)
@Composable
private fun FeedChipRowReadyGeneratorWithAvatarSelectedScreenshot() {
    val chips =
        persistentListOf(
            PinnedFeedUi("1", "following-uri", FeedKind.Following, "Following", null),
            PinnedFeedUi("2", "generator-discover-uri", FeedKind.Generator, "Discover", null),
            PinnedFeedUi("3", "generator-art-uri", FeedKind.Generator, "Art", "avatar-url-mock"),
        )
    NubecitaCanvasPreviewTheme {
        FeedChipRow(
            feedChips = chips,
            pinnedLists = persistentListOf(),
            selectedFeedUri = "generator-art-uri",
            status = FeedHostStatus.Ready,
            onSelectFeed = {},
            onSelectList = {},
            onRetry = {},
            onManageFeedsClick = {},
            onOpenListsSheet = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-with-lists-collapsed", showBackground = true)
@Composable
private fun FeedChipRowReadyWithListsCollapsedScreenshot() {
    val chips =
        persistentListOf(
            PinnedFeedUi("1", "following-uri", FeedKind.Following, "Following", null),
        )
    val lists =
        persistentListOf(
            PinnedFeedUi("4", "list-friends-uri", FeedKind.List, "Friends", null),
            PinnedFeedUi("5", "list-work-uri", FeedKind.List, "Work", null),
        )
    NubecitaCanvasPreviewTheme {
        FeedChipRow(
            feedChips = chips,
            pinnedLists = lists,
            selectedFeedUri = "following-uri",
            status = FeedHostStatus.Ready,
            onSelectFeed = {},
            onSelectList = {},
            onRetry = {},
            onManageFeedsClick = {},
            onOpenListsSheet = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-with-list-selected", showBackground = true)
@Composable
private fun FeedChipRowReadyWithListSelectedScreenshot() {
    val chips =
        persistentListOf(
            PinnedFeedUi("1", "following-uri", FeedKind.Following, "Following", null),
        )
    val lists =
        persistentListOf(
            PinnedFeedUi("4", "list-friends-uri", FeedKind.List, "Friends", null),
        )
    NubecitaCanvasPreviewTheme {
        FeedChipRow(
            feedChips = chips,
            pinnedLists = lists,
            selectedFeedUri = "list-friends-uri",
            status = FeedHostStatus.Ready,
            onSelectFeed = {},
            onSelectList = {},
            onRetry = {},
            onManageFeedsClick = {},
            onOpenListsSheet = {},
        )
    }
}

@PreviewTest
@Preview(name = "error-fallback", showBackground = true)
@Composable
private fun FeedChipRowErrorFallbackScreenshot() {
    val chips =
        persistentListOf(
            PinnedFeedUi("1", "following-uri", FeedKind.Following, "Following", null),
        )
    NubecitaCanvasPreviewTheme {
        FeedChipRow(
            feedChips = chips,
            pinnedLists = persistentListOf(),
            selectedFeedUri = "following-uri",
            status = FeedHostStatus.ErrorFallback,
            onSelectFeed = {},
            onSelectList = {},
            onRetry = {},
            onManageFeedsClick = {},
            onOpenListsSheet = {},
        )
    }
}

@PreviewTest
@Preview(name = "pinned-lists-sheet-layout", showBackground = true)
@Composable
private fun PinnedListsSheetLayoutScreenshot() {
    val lists =
        persistentListOf(
            PinnedFeedUi("4", "list-friends-uri", FeedKind.List, "Friends", null),
            PinnedFeedUi("5", "list-work-uri", FeedKind.List, "Work", null),
        )
    NubecitaCanvasPreviewTheme {
        PinnedListsSheet(
            pinnedLists = lists,
            selectedFeedUri = "list-friends-uri",
            onSelectList = {},
            onDismiss = {},
        )
    }
}
