package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

private const val MEDIA_GRID_COLUMNS = 3

/**
 * Contributes the Media tab body items to the enclosing LazyColumn.
 *
 * Row-packing approach — Bead D's design keeps the hero + sticky pill
 * tabs + active tab body in one LazyColumn so they share a single scroll
 * surface. A nested `LazyVerticalGrid` would break sticky headers AND
 * crash on the infinite-height constraint of a scrolling parent. The
 * trade-off: lose grid-native item-placement transitions; keep one
 * container shape and shared scroll state across all three tabs.
 *
 * Branches on [status]:
 * - [TabLoadStatus.Idle], [TabLoadStatus.InitialLoading] → shimmer skeletons
 * - [TabLoadStatus.InitialError] → error state + Retry
 * - [TabLoadStatus.Loaded] with empty items → empty state
 * - [TabLoadStatus.Loaded] with items → chunked rows of MediaCellThumb +
 *   optional appending row
 *
 * [onMediaTap] receives the tapped cell's `postUri` and is wired to
 * `ProfileEvent.PostTapped` at the screen level — identical chain to a
 * PostCard tap on Posts/Replies (proven by `ProfileViewModelTest`'s
 * Media-PostTapped regression test).
 */
internal fun LazyListScope.profileMediaTabBody(
    status: TabLoadStatus,
    onMediaTap: (postUri: String) -> Unit,
    onRetry: () -> Unit,
) {
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading,
        -> {
            item(key = "media-loading", contentType = "loading") {
                ProfileLoadingState()
            }
        }
        is TabLoadStatus.InitialError -> {
            item(key = "media-error", contentType = "error") {
                ProfileErrorState(error = status.error, onRetry = onRetry)
            }
        }
        is TabLoadStatus.Loaded -> {
            if (status.items.isEmpty()) {
                item(key = "media-empty", contentType = "empty") {
                    ProfileEmptyState(tab = ProfileTab.Media)
                }
            } else {
                // `filterIsInstance` is defensive — AuthorFeedMapper only
                // emits MediaCell for the Media tab. A future TabItemUi
                // variant would surface as a compile warning at the
                // exhaustiveness check in profileFeedTabBody.
                val cells = status.items.filterIsInstance<TabItemUi.MediaCell>()
                val rows = cells.chunked(MEDIA_GRID_COLUMNS)
                items(
                    items = rows,
                    key = { row -> row.joinToString(":") { it.postUri } },
                    contentType = { "media-row" },
                ) { row ->
                    MediaGridRow(row = row, onMediaTap = onMediaTap)
                }
                if (status.isAppending) {
                    item(key = "media-appending", contentType = "appending") {
                        ProfileAppendingIndicator()
                    }
                }
            }
        }
    }
}

/**
 * One row of the Media grid — up to [MEDIA_GRID_COLUMNS] cells. Short
 * trailing rows are left-padded with [Spacer]s so cells align with the
 * left edge of the grid.
 */
@Composable
private fun MediaGridRow(
    row: List<TabItemUi.MediaCell>,
    onMediaTap: (postUri: String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { cell ->
            MediaCellThumb(
                cell = cell,
                onClick = { onMediaTap(cell.postUri) },
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
        }
        repeat(MEDIA_GRID_COLUMNS - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
