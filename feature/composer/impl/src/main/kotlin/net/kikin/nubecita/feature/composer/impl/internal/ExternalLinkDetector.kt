package net.kikin.nubecita.feature.composer.impl.internal

/**
 * An external (web) URL found in the composer text, eligible for a link-preview
 * card.
 *
 * @property matchedText the exact substring matched. Unlike a quote link, the URL
 *   is NOT stripped from the text — it stays as a clickable facet; [matchedText]
 *   only feeds the memoized "already attempted" set so a dismissed/failed URL
 *   isn't re-detected on the next keystroke.
 */
internal data class ExternalLinkMatch(
    val matchedText: String,
)

/**
 * Detects a single arbitrary `http(s)` URL in composer text so the composer can
 * attach a link-preview card (nubecita-gfli).
 *
 * Returns the FIRST eligible URL — one that is **not** a Bluesky post quote-link
 * (those route to [QuoteLinkDetector]) and not in [exclude] (callers pass
 * already-attempted URLs) — or `null` when there's none. Trailing punctuation
 * (`).,;!?…` and matched brackets/quotes) is trimmed so a URL at the end of a
 * sentence resolves to the bare link.
 */
internal object ExternalLinkDetector {
    private val URL = Regex("""https?://\S+""")

    // Trailing characters that are almost always sentence punctuation, not part
    // of the URL. A closing bracket/paren/quote is only trimmed when unbalanced.
    private const val TRAILING_PUNCTUATION = ".,;:!?…\"'"

    fun detect(
        text: String,
        exclude: Set<String> = emptySet(),
    ): ExternalLinkMatch? {
        for (raw in URL.findAll(text)) {
            val trimmed = trimTrailing(raw.value)
            if (trimmed.isEmpty()) continue
            // Skip Bluesky post quote-links — those are handled as quotes.
            if (QuoteLinkDetector.detect(trimmed) != null) continue
            if (trimmed in exclude) continue
            return ExternalLinkMatch(matchedText = trimmed)
        }
        return null
    }

    private fun trimTrailing(url: String): String {
        var end = url.length
        while (end > 0) {
            val c = url[end - 1]
            val isPunct = TRAILING_PUNCTUATION.indexOf(c) >= 0
            val isUnbalancedCloser =
                (c == ')' && url.count { it == '(' } < url.count { it == ')' }) ||
                    (c == ']' && url.count { it == '[' } < url.count { it == ']' })
            if (isPunct || isUnbalancedCloser) end-- else break
        }
        return url.substring(0, end)
    }
}
