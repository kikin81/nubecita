package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoCommentsNestedScrollConnectionTest {
    @Test
    fun `upward drag when partially expanded expands sheet and consumes delta`() {
        var expandCalled = false
        var isPartiallyExpanded = true
        var isAtTop = true

        val connection =
            VideoCommentsNestedScrollConnection(
                isPartiallyExpanded = { isPartiallyExpanded },
                isAtTop = { isAtTop },
                onExpand = { expandCalled = true },
                onCollapse = {},
            )

        val consumed =
            connection.onPreScroll(
                available = Offset(0f, -40f),
                source = NestedScrollSource.UserInput,
            )

        assertTrue(expandCalled)
        assertEquals(Offset(0f, -40f), consumed)
    }

    @Test
    fun `upward drag when already expanded delegates to content and does not consume`() {
        var expandCalled = false
        var isPartiallyExpanded = false
        var isAtTop = true

        val connection =
            VideoCommentsNestedScrollConnection(
                isPartiallyExpanded = { isPartiallyExpanded },
                isAtTop = { isAtTop },
                onExpand = { expandCalled = true },
                onCollapse = {},
            )

        val consumed =
            connection.onPreScroll(
                available = Offset(0f, -40f),
                source = NestedScrollSource.UserInput,
            )

        assertFalse(expandCalled)
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `downward drag when not at top does not collapse sheet and does not consume in preScroll`() {
        var collapseCalled = false
        var isPartiallyExpanded = false
        var isAtTop = false

        val connection =
            VideoCommentsNestedScrollConnection(
                isPartiallyExpanded = { isPartiallyExpanded },
                isAtTop = { isAtTop },
                onExpand = {},
                onCollapse = { collapseCalled = true },
            )

        val consumed =
            connection.onPreScroll(
                available = Offset(0f, 40f),
                source = NestedScrollSource.UserInput,
            )

        assertFalse(collapseCalled)
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `downward drag when at top collapses sheet and consumes in postScroll`() {
        var collapseCalled = false
        var isPartiallyExpanded = false
        var isAtTop = true

        val connection =
            VideoCommentsNestedScrollConnection(
                isPartiallyExpanded = { isPartiallyExpanded },
                isAtTop = { isAtTop },
                onExpand = {},
                onCollapse = { collapseCalled = true },
            )

        val consumed =
            connection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 40f),
                source = NestedScrollSource.UserInput,
            )

        assertTrue(collapseCalled)
        assertEquals(Offset(0f, 40f), consumed)
    }

    @Test
    fun `downward drag when not at top does not collapse sheet in postScroll`() {
        var collapseCalled = false
        var isPartiallyExpanded = false
        var isAtTop = false

        val connection =
            VideoCommentsNestedScrollConnection(
                isPartiallyExpanded = { isPartiallyExpanded },
                isAtTop = { isAtTop },
                onExpand = {},
                onCollapse = { collapseCalled = true },
            )

        val consumed =
            connection.onPostScroll(
                consumed = Offset(0f, 40f),
                available = Offset.Zero,
                source = NestedScrollSource.UserInput,
            )

        assertFalse(collapseCalled)
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `upward fling when partially expanded triggers onExpand and consumes velocity`() =
        runTest {
            var expandCalled = false
            var isPartiallyExpanded = true

            val connection =
                VideoCommentsNestedScrollConnection(
                    isPartiallyExpanded = { isPartiallyExpanded },
                    isAtTop = { true },
                    onExpand = { expandCalled = true },
                    onCollapse = {},
                )

            val consumed =
                connection.onPreFling(
                    available = Velocity(0f, -600f),
                )

            assertTrue(expandCalled)
            assertEquals(Velocity(0f, -600f), consumed)
        }

    @Test
    fun `downward fling when at top triggers onCollapse and consumes velocity`() =
        runTest {
            var collapseCalled = false
            var isAtTop = true

            val connection =
                VideoCommentsNestedScrollConnection(
                    isPartiallyExpanded = { false },
                    isAtTop = { isAtTop },
                    onExpand = {},
                    onCollapse = { collapseCalled = true },
                )

            val consumed =
                connection.onPostFling(
                    consumed = Velocity.Zero,
                    available = Velocity(0f, 600f),
                )

            assertTrue(collapseCalled)
            assertEquals(Velocity(0f, 600f), consumed)
        }
}
