package net.kikin.nubecita.core.posting.internal

import net.kikin.nubecita.core.common.text.GraphemeCounter

/**
 * A hashtag or cashtag found in post text. [tag] is the facet value: no
 * leading `#`, case preserved for hashtags; `$` + uppercased ticker for
 * cashtags. [start] / [endExclusive] are UTF-16 indices into the text,
 * covering the `#`/`＃`/`$` through the end of the (stripped) tag.
 */
internal data class DetectedTag(
    val tag: String,
    val start: Int,
    val endExclusive: Int,
)

/**
 * Pure hashtag + cashtag detection, ported from the official client's
 * `TAG_REGEX`, `TRAILING_PUNCTUATION_REGEX` and `CASHTAG_REGEX`
 * (`bluesky-social/atproto` `packages/api/src/rich-text/util.ts`) and the
 * post-processing in `detection.ts`, so Nubecita publishes byte-identical
 * `app.bsky.richtext.facet#tag` facets to bsky.app.
 *
 * JS → JDK regex: JS's `\s` under the `u` flag is Unicode whitespace while the
 * JDK's is ASCII-only, so [WS] spells the JS set out explicitly. `\d` is
 * ASCII-only in both, and `\p{P}` is the Unicode punctuation category in both.
 */
internal object TagDetector {
    fun detect(text: String): List<DetectedTag> = (hashtags(text) + cashtags(text)).toList()

    private fun hashtags(text: String): Sequence<DetectedTag> =
        TAG_REGEX
            .findAll(text)
            .mapNotNull { match ->
                val raw = match.groups[TAG_GROUP]?.value ?: return@mapNotNull null
                // No trim(): the group can't hold whitespace, and Kotlin's trim()
                // would also strip \u001C-\u001F, which JS's trim() keeps.
                val tag = raw.replace(TRAILING_PUNCTUATION_REGEX, "")
                // UTF-16 length is always >= the grapheme count, so only pay for
                // the grapheme walk when the cheap length already exceeds the cap.
                if (tag.isEmpty() || (tag.length > MAX_TAG_GRAPHEMES && GraphemeCounter.count(tag) > MAX_TAG_GRAPHEMES)) {
                    return@mapNotNull null
                }
                val hashIndex = match.range.first + match.groupValues[LEADING_GROUP].length
                DetectedTag(tag = tag, start = hashIndex, endExclusive = hashIndex + 1 + tag.length)
            }

    private fun cashtags(text: String): Sequence<DetectedTag> =
        CASHTAG_REGEX
            .findAll(text)
            .mapNotNull { match ->
                val ticker = match.groups[TAG_GROUP]?.value ?: return@mapNotNull null
                val dollarIndex = match.range.first + match.groupValues[LEADING_GROUP].length
                DetectedTag(
                    tag = "$" + ticker.uppercase(),
                    start = dollarIndex,
                    endExclusive = dollarIndex + 1 + ticker.length,
                )
            }

    private const val MAX_TAG_GRAPHEMES = 64
    private const val LEADING_GROUP = 1
    private const val TAG_GROUP = 2

    // Regex sources are plain strings with doubled backslashes so the `\uXXXX`
    // escapes reach the regex engine as text. Raw strings would let formatters
    // inline them as invisible characters.

    // JS `\s` (Unicode-aware): ASCII whitespace plus the Unicode space separators,
    // line/paragraph separators, and the BOM.
    private const val WS = "\\t\\n\\x0B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

    // Zero-width / invisible characters the official regex excludes from tags.
    private const val ZW = "\\u00AD\\u2060\\u200A\\u200B\\u200C\\u200D\\u20E2"

    private val TAG_REGEX =
        Regex("(^|[$WS])[#\\uFF03]((?!\\uFE0F)[^$WS$ZW]*[^\\d$WS\\p{P}$ZW]+[^$WS$ZW]*)?")

    private val TRAILING_PUNCTUATION_REGEX = Regex("\\p{P}+$")

    private val CASHTAG_REGEX =
        Regex("(^|[$WS]|\\()\\$([A-Za-z][A-Za-z0-9]{0,4})(?=[$WS]|$|[.,;:!?)\"'\\u2019])")
}
