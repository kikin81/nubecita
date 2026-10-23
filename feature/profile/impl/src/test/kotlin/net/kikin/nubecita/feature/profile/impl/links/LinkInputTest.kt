package net.kikin.nubecita.feature.profile.impl.links

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LinkInputTest {
    @Test
    fun `bare domain gets https`() {
        assertEquals("https://patreon.com/sarah", normalizeLinkUrl("patreon.com/sarah"))
        assertEquals("https://example.com", normalizeLinkUrl("  example.com  "))
    }

    @Test
    fun `explicit scheme is kept, including http`() {
        assertEquals("https://example.com/x", normalizeLinkUrl("https://example.com/x"))
        assertEquals("http://example.com", normalizeLinkUrl("http://example.com"))
    }

    @Test
    fun `unusable input is rejected`() {
        assertNull(normalizeLinkUrl(""))
        assertNull(normalizeLinkUrl("not a url"))
        assertNull(normalizeLinkUrl("localhost"))
        assertNull(normalizeLinkUrl("example.c"))
        assertNull(normalizeLinkUrl("example.123"))
        assertNull(normalizeLinkUrl("example.com."))
        assertNull(normalizeLinkUrl("ftp://example.com"))
        assertNull(normalizeLinkUrl("https://.example.com"))
    }

    @Test
    fun `http is insecure, https is not`() {
        assertTrue(isInsecureLink("http://example.com"))
        assertFalse(isInsecureLink("https://example.com"))
    }

    @Test
    fun `known shorteners are flagged, brand short links and look-alikes are not`() {
        assertTrue(isShortenerLink("https://bit.ly/abc"))
        assertTrue(isShortenerLink("https://www.tinyurl.com/abc"))
        assertFalse(isShortenerLink("https://youtu.be/abc"))
        assertFalse(isShortenerLink("https://notbit.ly/abc"))
    }
}
