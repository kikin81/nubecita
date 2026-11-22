package net.kikin.nubecita.feature.search.impl

private const val DEFAULT_HANDLE_SUFFIX = ".bsky.social"

/**
 * The character ranges of [text] to highlight for [query], case-insensitive and
 * non-overlapping, in order.
 *
 * A handle ending in the default `.bsky.social` suffix is only searched in its
 * first label: every account on that host shares the suffix, so a query like
 * "al" lighting up the end of "social" says nothing about why the account
 * matched. A custom-domain handle, and any display name ([isHandle] false), is
 * searched in full.
 */
internal fun highlightRanges(
    text: String,
    query: String,
    isHandle: Boolean,
): List<IntRange> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    val searchable =
        if (isHandle && text.endsWith(DEFAULT_HANDLE_SUFFIX, ignoreCase = true)) {
            text.substringBefore('.').length
        } else {
            text.length
        }
    val haystack = text.lowercase()
    return buildList {
        var from = 0
        while (from <= searchable - needle.length) {
            val start = haystack.indexOf(needle, startIndex = from)
            if (start < 0 || start + needle.length > searchable) break
            add(start until start + needle.length)
            from = start + needle.length
        }
    }
}
