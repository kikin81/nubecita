package net.kikin.nubecita.feature.postdetail.impl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import net.kikin.nubecita.data.models.ThreadItem

/**
 * Keeps the opened post at the top of post detail, with the posts before it
 * reachable by scrolling up.
 *
 * A thread opened in its middle arrives as "earlier posts, the opened post,
 * replies". Rendered as given, the list would start at the earliest post, and
 * opening post 30 of 40 would land the reader on post 1. So the rows above the
 * opened post are held back until it has been laid out as the first row, and
 * only then added: the list keeps its first visible row by key, so the opened
 * post stays where it is and the earlier posts appear above the fold. No
 * scroll call is involved, so there is no frame where the list visibly jumps.
 *
 * One more thing has to be true for that to work. A list cannot scroll its
 * last row above the bottom of the viewport, so a thread with little under the
 * opened post could not hold it at the top. [PinnedThread.trailingSpacePx] is
 * the room the list adds under such a thread to make up the difference.
 */
@Immutable
internal data class PinnedThread(
    /** The rows to render now: all of them, or only the opened post and below. */
    val rows: ImmutableList<ThreadItem>,
    /** Whether the list carries a trailing spacer row (it does when rows sit above the opened post). */
    val hasTrailingSpacer: Boolean,
    val trailingSpacePx: Int,
)

/** One laid-out list row, as far as the pinning arithmetic cares. */
internal data class ThreadRowInfo(
    val index: Int,
    val key: Any,
    val size: Int,
)

/** How many rows sit above the opened post; 0 when it leads, or when there is none. */
internal fun List<ThreadItem>.leadingRowCount(): Int = indexOfFirst { it is ThreadItem.Focus }.coerceAtLeast(0)

/** Every row, or — until the rows above the opened post may be shown — the opened post and below. */
internal fun List<ThreadItem>.rowsToDisplay(leadingRowsShown: Boolean): List<ThreadItem> {
    val leading = leadingRowCount()
    return if (leadingRowsShown || leading == 0) this else drop(leading)
}

/**
 * The room to add under the thread so the opened post can sit at the top of
 * the viewport, or null while the list has not laid anything out yet.
 *
 * [visible] are the rows on screen, [lastContentIndex] the index of the last
 * real row (the spacer itself comes after it and is never counted), and
 * [viewportContentHeight] the viewport minus the list's content padding.
 *
 * It is 0 whenever nothing needs propping up: the rows from the opened post
 * down already fill the viewport, they run on past it, or the opened post is
 * not on screen at all.
 */
internal fun trailingSpaceToPinFocus(
    visible: List<ThreadRowInfo>,
    focusKey: Any,
    lastContentIndex: Int,
    viewportContentHeight: Int,
): Int? {
    if (visible.isEmpty()) return null
    val focus = visible.firstOrNull { it.key == focusKey } ?: return 0
    val tail = visible.filter { it.index in focus.index..lastContentIndex }
    if (tail.isEmpty() || tail.last().index < lastContentIndex) return 0
    return (viewportContentHeight - tail.sumOf { it.size }).coerceAtLeast(0)
}

/**
 * [items] as post detail should render them right now, plus the trailing room
 * the list needs. See [PinnedThread].
 *
 * [showLeadingRowsImmediately] skips the hold-back; it exists for previews and
 * screenshot fixtures, which capture a single frame and would otherwise never
 * show the rows above the opened post.
 */
@Composable
internal fun rememberPinnedThread(
    items: ImmutableList<ThreadItem>,
    listState: LazyListState,
    showLeadingRowsImmediately: Boolean = false,
): PinnedThread {
    val focusKey = remember(items) { items.firstOrNull { it is ThreadItem.Focus }?.key }
    val hasLeadingRows = remember(items) { items.leadingRowCount() > 0 }
    // Null until the opened post has been laid out as the first row. Keyed on
    // whether rows sit above it, so a thread that first rendered as the opened
    // post alone (from cache) and then gains its earlier posts goes through the
    // hold-back again, this time with the room it needs.
    var trailingSpacePx by rememberSaveable(focusKey, hasLeadingRows) {
        mutableStateOf(if (showLeadingRowsImmediately) 0 else null)
    }
    val leadingRowsShown = !hasLeadingRows || trailingSpacePx != null
    val rows = remember(items, leadingRowsShown) { items.rowsToDisplay(leadingRowsShown).toImmutableList() }

    if (hasLeadingRows && focusKey != null) {
        LaunchedEffect(listState, focusKey) {
            snapshotFlow {
                val info = listState.layoutInfo
                trailingSpaceToPinFocus(
                    visible = info.visibleItemsInfo.map { ThreadRowInfo(index = it.index, key = it.key, size = it.size) },
                    focusKey = focusKey,
                    // The spacer is the last row; the one before it is the last real one.
                    lastContentIndex = info.totalItemsCount - 2,
                    viewportContentHeight = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding,
                )
            }.filterNotNull()
                .distinctUntilChanged()
                .collect { trailingSpacePx = it }
        }
    }

    return PinnedThread(
        rows = rows,
        hasTrailingSpacer = hasLeadingRows,
        trailingSpacePx = if (hasLeadingRows) trailingSpacePx ?: 0 else 0,
    )
}
