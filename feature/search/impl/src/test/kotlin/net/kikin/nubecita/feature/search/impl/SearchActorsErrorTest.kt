package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class SearchActorsErrorTest {
    @Test
    fun ioException_mapsToNetwork() {
        val mapped = IOException("connection reset").toSearchActorsError()
        assertEquals(SearchActorsError.Network, mapped)
    }

    @Test
    fun unknownThrowable_mapsToUnknown_withMessage() {
        val mapped = IllegalStateException("totally unexpected").toSearchActorsError()
        assertTrue(mapped is SearchActorsError.Unknown)
        assertEquals("totally unexpected", (mapped as SearchActorsError.Unknown).cause)
    }

    @Test
    fun throwableWithNullMessage_mapsToUnknown_withNullCause() {
        val mapped = RuntimeException().toSearchActorsError()
        assertTrue(mapped is SearchActorsError.Unknown)
        assertEquals(null, (mapped as SearchActorsError.Unknown).cause)
    }
}
