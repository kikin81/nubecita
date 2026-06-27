package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExternalLinkDetectorTest {
    @Test
    fun detects_a_plain_https_url() {
        val match = ExternalLinkDetector.detect("check this https://example.com/article out")
        assertEquals("https://example.com/article", match?.matchedText)
    }

    @Test
    fun trims_trailing_sentence_punctuation() {
        assertEquals(
            "https://example.com/page",
            ExternalLinkDetector.detect("see https://example.com/page.")?.matchedText,
        )
        assertEquals(
            "https://example.com/page",
            ExternalLinkDetector.detect("(see https://example.com/page)")?.matchedText,
        )
    }

    @Test
    fun trims_unbalanced_trailing_paren_without_overtrimming_balanced() {
        // `…/(page))` — only the unbalanced outer `)` is trimmed; the balanced
        // inner `(page)` is kept.
        assertEquals(
            "https://example.com/(page)",
            ExternalLinkDetector.detect("see (https://example.com/(page))")?.matchedText,
        )
    }

    @Test
    fun excludes_bluesky_web_quote_link() {
        // A bsky.app post link is a quote, handled by QuoteLinkDetector — not an external card.
        assertNull(
            ExternalLinkDetector.detect("https://bsky.app/profile/alice.bsky.social/post/3kabc"),
        )
    }

    @Test
    fun excludes_at_uri_quote_link() {
        assertNull(
            ExternalLinkDetector.detect("at://did:plc:abc/app.bsky.feed.post/3kabc"),
        )
    }

    @Test
    fun picks_first_external_url_skipping_a_leading_quote_link() {
        val text =
            "https://bsky.app/profile/alice.bsky.social/post/3kabc and https://example.com/news"
        assertEquals("https://example.com/news", ExternalLinkDetector.detect(text)?.matchedText)
    }

    @Test
    fun respects_exclude_set() {
        val text = "https://example.com/one https://example.com/two"
        assertEquals(
            "https://example.com/two",
            ExternalLinkDetector.detect(text, exclude = setOf("https://example.com/one"))?.matchedText,
        )
    }

    @Test
    fun returns_null_when_no_url() {
        assertNull(ExternalLinkDetector.detect("just some text, no links here"))
    }
}
