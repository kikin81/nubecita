package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
    onMediaTap: (cell: TabItemUi.MediaCell) -> Unit,
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
                // chunked() yields List<List<MediaCell>>; map each row
                // to ImmutableList so MediaGridRow's `row` parameter is
                // Compose-stable (a raw `List` is flagged @Unstable and
                // would defeat skipping when the parent recomposes).
                val rows = cells.chunked(MEDIA_GRID_COLUMNS).map { it.toImmutableList() }
                items(
                    items = rows,
                    // first cell's postUri uniquely identifies the row
                    // inside this page — cheaper than joining 1-3 URIs.
                    key = { row -> row.first().postUri },
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
 *
 * [row] is typed [ImmutableList] so Compose can flag this Composable as
 * skippable when the parent recomposes with an equal row.
 */
@Composable
private fun MediaGridRow(
    row: ImmutableList<TabItemUi.MediaCell>,
    onMediaTap: (cell: TabItemUi.MediaCell) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { cell ->
            MediaCellThumb(
                cell = cell,
                onClick = { onMediaTap(cell) },
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
        }
        repeat(MEDIA_GRID_COLUMNS - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
