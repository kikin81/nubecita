package net.kikin.nubecita.feature.profile.impl.links

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ProfileLinkTextTest {
    @Test
    fun `label prefers a non-blank title`() {
        assertEquals("Shop", linkLabel("Shop", "https://example.com"))
        assertEquals("Shop", linkLabel("  Shop  ", "https://example.com"))
    }

    @Test
    fun `label falls back to the host without www`() {
        assertEquals("example.com", linkLabel(null, "https://www.example.com/x"))
        assertEquals("example.com", linkLabel("   ", "https://example.com"))
        assertEquals("sub.example.com", linkLabel(null, "https://sub.example.com"))
    }

    @Test
    fun `underscore hostnames still yield a clean host`() {
        assertEquals("my_portfolio.com", linkLabel(null, "https://www.my_portfolio.com"))
        assertEquals("my_site.example.com", linkHost("https://user@my_site.example.com:8443/x"))
        assertEquals(true, isSupportLink("https://my_name.itch.io"))
    }

    @Test
    fun `host of an unparseable url is the raw string`() {
        assertEquals("not a url", linkHost("not a url"))
    }

    @Test
    fun `parseWebUrl accepts only http and https`() {
        assertEquals("example.com", parseWebUrl("HTTPS://Example.com/Path")?.host)
        assertNull(parseWebUrl("ftp://example.com"))
        assertNull(parseWebUrl("https://"))
    }

    @Test
    fun `pill label is cut at 30 characters with an ellipsis`() {
        val forty = "a".repeat(40)
        assertEquals("a".repeat(30) + "…", truncatePillLabel(forty))
        assertEquals("a".repeat(30), truncatePillLabel("a".repeat(30)))
    }

    @Test
    fun `overflow keeps everything at four or fewer`() {
        assertEquals(listOf(1, 2, 3, 4) to 0, splitForOverflow(listOf(1, 2, 3, 4)))
        assertEquals(emptyList<Int>() to 0, splitForOverflow(emptyList<Int>()))
    }

    @Test
    fun `overflow shows four and counts the rest`() {
        assertEquals(listOf(1, 2, 3, 4) to 3, splitForOverflow(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals(listOf(1, 2, 3, 4) to 6, splitForOverflow((1..10).toList()))
    }
}
