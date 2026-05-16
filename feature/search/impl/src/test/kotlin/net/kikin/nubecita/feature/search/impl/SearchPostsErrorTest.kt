package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class SearchPostsErrorTest {
    @Test
    fun ioException_mapsToNetwork() {
        val mapped = IOException("connection reset").toSearchPostsError()
        assertEquals(SearchPostsError.Network, mapped)
    }

    @Test
    fun unknownThrowable_mapsToUnknown_withMessage() {
        val mapped = IllegalStateException("totally unexpected").toSearchPostsError()
        assertTrue(mapped is SearchPostsError.Unknown)
        assertEquals("totally unexpected", (mapped as SearchPostsError.Unknown).cause)
    }

    @Test
    fun throwableWithNullMessage_mapsToUnknown_withNullCause() {
        val mapped = RuntimeException().toSearchPostsError()
        assertTrue(mapped is SearchPostsError.Unknown)
        assertEquals(null, (mapped as SearchPostsError.Unknown).cause)
    }
}
