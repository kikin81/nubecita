package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [QuoteLinkDetector] — the pure paste-a-link parser
 * (nubecita-8g28.4). Covers both link forms, embedding in surrounding text,
 * the handle/DID authority cases, the exclude set, and non-matches.
 */
class QuoteLinkDetectorTest {
    @Test
    fun `bsky web link with handle resolves to at-uri with that authority`() {
        val match = QuoteLinkDetector.detect("https://bsky.app/profile/alice.bsky.social/post/3kabc")

        assertEquals("https://bsky.app/profile/alice.bsky.social/post/3kabc", match?.matchedText)
        assertEquals("at://alice.bsky.social/app.bsky.feed.post/3kabc", match?.atUri)
    }

    @Test
    fun `bsky web link with did authority is preserved`() {
        val match = QuoteLinkDetector.detect("https://bsky.app/profile/did:plc:abc123/post/3kxyz")

        assertEquals("at://did:plc:abc123/app.bsky.feed.post/3kxyz", match?.atUri)
    }

    @Test
    fun `at-uri is detected verbatim`() {
        val match = QuoteLinkDetector.detect("at://did:plc:bob/app.bsky.feed.post/3kqqq")

        assertEquals("at://did:plc:bob/app.bsky.feed.post/3kqqq", match?.matchedText)
        assertEquals("at://did:plc:bob/app.bsky.feed.post/3kqqq", match?.atUri)
    }

    @Test
    fun `link embedded in surrounding text is found`() {
        val match =
            QuoteLinkDetector.detect("look at this https://bsky.app/profile/carol.test/post/3kp9 wild")

        assertEquals("https://bsky.app/profile/carol.test/post/3kp9", match?.matchedText)
        assertEquals("at://carol.test/app.bsky.feed.post/3kp9", match?.atUri)
    }

    @Test
    fun `trailing slash and query params are matched and stripped, at-uri stays clean`() {
        val match =
            QuoteLinkDetector.detect("https://www.bsky.app/profile/alice.test/post/3kp9/?ref_src=share")

        // The whole URL (incl. www, trailing slash, query) is the matched text so
        // it strips cleanly…
        assertEquals("https://www.bsky.app/profile/alice.test/post/3kp9/?ref_src=share", match?.matchedText)
        // …while the at-uri is built from just authority + rkey.
        assertEquals("at://alice.test/app.bsky.feed.post/3kp9", match?.atUri)
    }

    @Test
    fun `no link returns null`() {
        assertNull(QuoteLinkDetector.detect("just some text, no links here"))
        // A profile link (not a post link) must not match.
        assertNull(QuoteLinkDetector.detect("https://bsky.app/profile/alice.bsky.social"))
    }

    @Test
    fun `excluded link is skipped`() {
        val url = "https://bsky.app/profile/alice.bsky.social/post/3kabc"
        assertNull(QuoteLinkDetector.detect(url, exclude = setOf(url)))
    }

    @Test
    fun `first eligible link is returned when one is excluded`() {
        val excluded = "https://bsky.app/profile/alice.test/post/3kone"
        val text = "$excluded and at://did:plc:bob/app.bsky.feed.post/3ktwo"

        val match = QuoteLinkDetector.detect(text, exclude = setOf(excluded))

        assertEquals("at://did:plc:bob/app.bsky.feed.post/3ktwo", match?.atUri)
    }
}
