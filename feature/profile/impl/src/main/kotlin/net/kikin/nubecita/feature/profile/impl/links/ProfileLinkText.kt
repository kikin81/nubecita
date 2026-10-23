package net.kikin.nubecita.feature.profile.impl.links

import java.net.URI
import java.net.URISyntaxException

/** Pills cut labels at this many characters (the official `MAX_TITLE_LENGTH`). */
internal const val MAX_PILL_LABEL_LENGTH = 30

/** The profile row renders at most this many link pills before "+N". */
internal const val MAX_VISIBLE_LINK_PILLS = 4

/** The scheme / lowercase host / path of an http(s) URL. */
internal data class WebUrl(
    val scheme: String,
    val host: String,
    val path: String,
)

/**
 * Parses an `http`/`https` URL, or returns null for anything else (other
 * schemes, no host, malformed). `java.net.URI` rather than `android.net.Uri`
 * so this stays JVM-unit-testable.
 */
internal fun parseWebUrl(url: String): WebUrl? {
    val uri =
        try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return null
        }
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return WebUrl(scheme = scheme, host = host, path = uri.rawPath.orEmpty())
}

/** The URL's host without a leading `www.`, or the raw string when it doesn't parse. */
internal fun linkHost(url: String): String = parseWebUrl(url)?.host?.removePrefix("www.") ?: url

/**
 * The text a link shows: its [title] when non-blank (Bluesky's "custom
 * title"), else the destination host — the lexicon's suggested fallback.
 */
internal fun linkLabel(
    title: String?,
    url: String,
): String = title?.trim()?.takeIf { it.isNotEmpty() } ?: linkHost(url)

/** Cuts [label] to [MAX_PILL_LABEL_LENGTH] characters with an ellipsis. */
internal fun truncatePillLabel(label: String): String =
    if (label.length <= MAX_PILL_LABEL_LENGTH) {
        label
    } else {
        label.take(MAX_PILL_LABEL_LENGTH).trimEnd() + "…"
    }

/**
 * The profile row's deterministic overflow split: the pills to render, and how
 * many links fold into the "+N" pill (0 = no overflow pill). No measurement —
 * at most [MAX_VISIBLE_LINK_PILLS] pills, always.
 */
internal fun <T> splitForOverflow(links: List<T>): Pair<List<T>, Int> =
    if (links.size <= MAX_VISIBLE_LINK_PILLS) {
        links to 0
    } else {
        links.take(MAX_VISIBLE_LINK_PILLS) to links.size - MAX_VISIBLE_LINK_PILLS
    }
