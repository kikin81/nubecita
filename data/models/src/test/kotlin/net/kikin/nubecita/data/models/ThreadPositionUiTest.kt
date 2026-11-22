package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [ThreadPositionUi.of] is the single place the server's thread numbering is
 * validated; everything that shows "3/5" goes through it.
 */
class ThreadPositionUiTest {
    @Test
    fun `a valid index and count become a position`() {
        assertEquals(ThreadPositionUi(index = 3, count = 5), ThreadPositionUi.of(index = 3, count = 5))
        assertEquals(ThreadPositionUi(index = 1, count = 1), ThreadPositionUi.of(index = 1, count = 1))
    }

    @Test
    fun `an out-of-range pair is no position`() {
        listOf(0L to 5L, 6L to 5L, 3L to 0L, -1L to 5L).forEach { (index, count) ->
            assertNull(ThreadPositionUi.of(index, count), "index=$index count=$count")
        }
    }

    @Test
    fun `a missing field is no position`() {
        assertNull(ThreadPositionUi.of(index = null, count = 5))
        assertNull(ThreadPositionUi.of(index = 3, count = null))
    }

    @Test
    fun `a count that does not fit an Int is no position`() {
        assertNull(ThreadPositionUi.of(index = 1, count = Int.MAX_VALUE.toLong() + 1))
    }

    @Test
    fun `root is the first post of the same thread`() {
        assertEquals(ThreadPositionUi(index = 1, count = 5), ThreadPositionUi(index = 3, count = 5).root())
    }

    @Test
    fun `parent is the post before it and the first post has none`() {
        assertEquals(ThreadPositionUi(index = 2, count = 5), ThreadPositionUi(index = 3, count = 5).parent())
        assertNull(ThreadPositionUi(index = 1, count = 5).parent())
    }
}
