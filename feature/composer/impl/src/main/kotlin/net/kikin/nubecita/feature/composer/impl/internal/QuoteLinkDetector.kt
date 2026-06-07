package net.kikin.nubecita.feature.composer.impl.internal

/**
 * A Bluesky post link found in the composer text, ready to attach as a quote.
 *
 * @property matchedText the exact substring matched (so the caller can strip it
 *   from the field once the quote resolves).
 * @property atUri the `at://…/app.bsky.feed.post/…` URI to fetch. For a
 *   `bsky.app/profile/{authority}/post/{rkey}` web link this is rebuilt from the
 *   URL's authority (handle or DID) + rkey; for a pasted `at://` URI it is the
 *   match verbatim.
 */
internal data class QuoteLinkMatch(
    val matchedText: String,
    val atUri: String,
)

/**
 * Detects a single Bluesky post link in composer text so the composer can attach
 * it as a quote (paste-a-link, nubecita-8g28.4).
 *
 * Recognizes two forms:
 * - web: `https://bsky.app/profile/{handle-or-did}/post/{rkey}`
 * - at-uri: `at://{handle-or-did}/app.bsky.feed.post/{rkey}`
 *
 * Returns the FIRST match whose matched text is not in [exclude] (callers pass
 * already-attempted links so a rejected/failed link isn't re-detected on the next
 * keystroke), or `null` when there's no eligible link.
 */
internal object QuoteLinkDetector {
    // Authority = handle (letters/digits/.-_) or DID (adds ':'); '%' tolerates
    // percent-encoding. The class excludes '/' so it stops at the path segment.
    private const val AUTHORITY = "[A-Za-z0-9._:%~-]+"
    private const val RKEY = "[A-Za-z0-9._~-]+"

    // Tolerate an optional `www.`, a trailing slash, and a query string so they're
    // included in the match (and thus stripped cleanly); the authority + rkey
    // capture groups still drive the at-uri, so the query never leaks into it.
    private val WEB =
        Regex("""https?://(?:www\.)?bsky\.app/profile/($AUTHORITY)/post/($RKEY)/?(?:\?[A-Za-z0-9._~%&=+-]*)?""")
    private val AT = Regex("""at://$AUTHORITY/app\.bsky\.feed\.post/$RKEY""")

    fun detect(
        text: String,
        exclude: Set<String> = emptySet(),
    ): QuoteLinkMatch? {
        val candidates =
            buildList {
                WEB.findAll(text).forEach { m ->
                    add(
                        m.range.first to
                            QuoteLinkMatch(
                                matchedText = m.value,
                                atUri = "at://${m.groupValues[1]}/app.bsky.feed.post/${m.groupValues[2]}",
                            ),
                    )
                }
                AT.findAll(text).forEach { m ->
                    add(m.range.first to QuoteLinkMatch(matchedText = m.value, atUri = m.value))
                }
            }
        return candidates
            .sortedBy { it.first }
            .map { it.second }
            .firstOrNull { it.matchedText !in exclude }
    }
}
