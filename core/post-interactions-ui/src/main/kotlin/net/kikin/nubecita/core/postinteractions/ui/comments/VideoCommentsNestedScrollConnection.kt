package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Custom [NestedScrollConnection] coordinating gesture arbitration between
 * an adaptive comments sheet and an internal comments `LazyColumn`.
 *
 * Implements OpenSpec decision D1:
 * - **Upward Drag**: Prioritizes expanding the sheet from `PartiallyExpanded`
 *   to `Expanded`. When the sheet is partially expanded, upward scroll delta
 *   is consumed to expand the sheet before delegating scroll to the comments list.
 * - **Content Scroll**: Once the sheet is `Expanded`, upward and downward deltas
 *   are delegated to the comments `LazyColumn`.
 * - **Downward Drag**: The sheet is only collapsed downward when the comments
 *   list has scrolled completely to the top (`isAtTop() == true`, i.e.
 *   `firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`).
 */
class VideoCommentsNestedScrollConnection(
    private val isPartiallyExpanded: () -> Boolean,
    private val isAtTop: () -> Boolean,
    private val onExpand: () -> Unit,
    private val onCollapse: () -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val deltaY = available.y
        // Upward drag: available.y < 0
        // When partially expanded, consume the delta to expand the sheet before scrolling content.
        if (deltaY < 0f && isPartiallyExpanded()) {
            onExpand()
            return Offset(0f, deltaY)
        }
        // Downward drag: available.y > 0
        // If content is scrolled, do NOT consume in preScroll; let LazyColumn scroll down.
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val deltaY = available.y
        // Downward drag: available.y > 0
        // When content is at the top (LazyColumn did not consume the delta), collapse the sheet.
        if (deltaY > 0f && isAtTop()) {
            onCollapse()
            return Offset(0f, deltaY)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Upward fling: available.y < 0
        if (available.y < 0f && isPartiallyExpanded()) {
            onExpand()
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        // Downward fling: available.y > 0
        if (available.y > 0f && isAtTop()) {
            onCollapse()
            return available
        }
        return Velocity.Zero
    }
}

/**
 * Remembers a [VideoCommentsNestedScrollConnection] scoped to the provided state queries and callbacks.
 */
@Composable
fun rememberVideoCommentsNestedScrollConnection(
    isPartiallyExpanded: () -> Boolean,
    isAtTop: () -> Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
): VideoCommentsNestedScrollConnection =
    remember(isPartiallyExpanded, isAtTop, onExpand, onCollapse) {
        VideoCommentsNestedScrollConnection(
            isPartiallyExpanded = isPartiallyExpanded,
            isAtTop = isAtTop,
            onExpand = onExpand,
            onCollapse = onCollapse,
        )
    }
