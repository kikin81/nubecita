package net.kikin.nubecita.feature.profile.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BioLinksTest {
    @Test
    fun `detects a single https url and maps its range back to the url`() {
        val bio = "check out https://nubecita.app for more"
        val links = detectLinkRanges(bio)

        assertEquals(1, links.size)
        val (range, url) = links.single()
        assertEquals("https://nubecita.app", url)
        // The range must slice the exact URL out of the original text.
        assertEquals(url, bio.substring(range))
    }

    @Test
    fun `detects a url at the very start of the bio`() {
        val bio = "https://example.com is my site"
        val (range, url) = detectLinkRanges(bio).single()

        assertEquals("https://example.com", url)
        assertEquals(url, bio.substring(range))
    }

    @Test
    fun `detects multiple urls`() {
        val bio = "site https://a.com and blog http://b.org/path?q=1 too"
        val urls = detectLinkRanges(bio).map { it.second }

        assertEquals(listOf("https://a.com", "http://b.org/path?q=1"), urls)
    }

    @Test
    fun `returns nothing when there are no urls`() {
        assertTrue(detectLinkRanges("just a normal bio with no links").isEmpty())
    }

    @Test
    fun `does not treat an at-handle or email as a link`() {
        // Leading `[^\w@]` guard keeps @mentions / emails out of the URL match.
        assertTrue(detectLinkRanges("reach me at alice@example.com or @alice.bsky.social").isEmpty())
    }

    @Test
    fun `each detected range slices its own url out of the text`() {
        val bio = "one https://one.app two https://two.dev done"
        val links = detectLinkRanges(bio)

        // Guard against a vacuous pass: there really are two links.
        assertEquals(2, links.size)
        links.forEach { (range, url) ->
            assertEquals(url, bio.substring(range))
        }
    }

    @Test
    fun `does not detect a url whose tld exceeds the regex cap`() {
        // Documents the shared composer-regex limitation: TLDs are capped at 6
        // chars, so a bare 7-char pseudo-tld like ".example" is not linkified.
        assertTrue(detectLinkRanges("see https://alice.example here").isEmpty())
    }
}
