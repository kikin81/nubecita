package net.kikin.nubecita.feature.search.impl.data

import java.net.URLDecoder

/** Where a [TrendUi.link] leads, as far as the app knows how to open it. */
internal sealed interface TrendLink {
    /** A custom feed. [actor] is the creator's DID or handle. */
    data class Feed(
        val actor: String,
        val rkey: String,
    ) : TrendLink

    /** A starter pack. [actor] is the creator's DID or handle. */
    data class StarterPack(
        val actor: String,
        val rkey: String,
    ) : TrendLink

    /** A search for [query]. */
    data class Search(
        val query: String,
    ) : TrendLink

    /** A shape the app does not recognise; callers fall back to searching the trend's name. */
    data object Unknown : TrendLink
}

private const val WEB_HOST_PREFIX = "https://bsky.app"

/**
 * Reads a trend's `link`: `/profile/{actor}/feed/{rkey}`, `/starter-pack/{actor}/{rkey}`
 * or `/search?q=…`. An absolute `https://bsky.app` prefix, a trailing slash and extra
 * query parameters are tolerated; anything else is [TrendLink.Unknown].
 */
internal fun parseTrendLink(link: String): TrendLink {
    val relative = link.removePrefix(WEB_HOST_PREFIX)
    val path = relative.substringBefore('?').trimEnd('/')
    val query = relative.substringAfter('?', missingDelimiterValue = "")
    val segments = path.split('/')
    // A leading "/" yields an empty first segment.
    if (segments.firstOrNull() != "") return TrendLink.Unknown
    val parts = segments.drop(1)
    return when {
        parts == listOf("search") -> searchQuery(query)?.let(TrendLink::Search) ?: TrendLink.Unknown
        parts.size == 4 && parts[0] == "profile" && parts[2] == "feed" && parts[1].isNotBlank() && parts[3].isNotBlank() ->
            TrendLink.Feed(actor = parts[1], rkey = parts[3])
        parts.size == 3 && parts[0] == "starter-pack" && parts[1].isNotBlank() && parts[2].isNotBlank() ->
            TrendLink.StarterPack(actor = parts[1], rkey = parts[2])
        else -> TrendLink.Unknown
    }
}

private fun searchQuery(queryString: String): String? =
    queryString
        .split('&')
        .firstOrNull { it.startsWith("q=") }
        ?.removePrefix("q=")
        ?.let { URLDecoder.decode(it, "UTF-8") }
        ?.takeIf { it.isNotBlank() }
