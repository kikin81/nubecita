package net.kikin.nubecita.core.klipy.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class KlipyKeyRedactingLoggerTest {
    private val captured = mutableListOf<String>()

    private fun logger(apiKey: String) = KlipyKeyRedactingLogger(apiKey = apiKey, delegate = captured::add)

    @Test
    fun `masks every occurrence of the key in the request line`() {
        val key = "SUPERSECRETKEY123"
        logger(key).log("REQUEST: https://api.klipy.com/api/v1/$key/gifs/search?q=cat")

        val out = captured.single()
        assertFalse(out.contains(key), "key must not appear in log output")
        assertEquals(
            "REQUEST: https://api.klipy.com/api/v1/***/gifs/search?q=cat",
            out,
        )
    }

    @Test
    fun `masks the key when it appears more than once`() {
        val key = "abc123"
        logger(key).log("$key and again $key")

        assertEquals("*** and again ***", captured.single())
    }

    @Test
    fun `leaves the message untouched when the key is blank`() {
        val message = "REQUEST: https://api.klipy.com/api/v1//gifs/search"
        logger("").log(message)

        assertEquals(message, captured.single())
    }
}
