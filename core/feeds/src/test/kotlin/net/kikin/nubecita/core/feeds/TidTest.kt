package net.kikin.nubecita.core.feeds

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TidTest {
    @Test
    fun `next returns a 13-character base32-sortable string`() {
        val tid = Tid.next()
        assertEquals(13, tid.length)
        assertTrue(tid.all { it in "234567abcdefghijklmnopqrstuvwxyz" }, "TID must use only base32-sortable chars: $tid")
    }

    @Test
    fun `two rapid next calls produce strictly increasing unique TIDs`() {
        val first = Tid.next()
        val second = Tid.next()

        assertTrue(first < second, "TIDs must be strictly increasing: first=$first, second=$second")
        assertTrue(first != second, "TIDs must be unique: got $first twice")
    }

    @Test
    fun `many rapid next calls are all strictly increasing`() {
        val tids = List(1_000) { Tid.next() }
        for (i in 1 until tids.size) {
            assertTrue(
                tids[i - 1] < tids[i],
                "TID at index ${i - 1} (${tids[i - 1]}) must be < TID at $i (${tids[i]})",
            )
        }
        assertEquals(tids.size, tids.toSet().size, "All TIDs must be unique")
    }
}
