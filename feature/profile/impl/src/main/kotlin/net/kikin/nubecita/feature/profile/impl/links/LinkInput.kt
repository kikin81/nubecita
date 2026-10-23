package net.kikin.nubecita.feature.profile.impl.links

/** `app.bsky.actor.profile#links` holds at most this many refs. */
internal const val MAX_PROFILE_LINKS = 10

/** `app.bsky.actor.link#title` limit (graphemes). */
internal const val MAX_LINK_TITLE_GRAPHEMES = 40

private val TLD = Regex("^[a-z]{2,}$")

/**
 * Normalizes what someone typed into a canonical web URL, or returns null when
 * it isn't a usable link. Mirrors the official client's `definitelyUrl`:
 * a missing scheme becomes `https://` (`patreon.com/name` →
 * `https://patreon.com/name`), the host needs at least two labels, and the TLD
 * must be 2+ letters. `http://` input is kept as typed — the form warns about
 * it rather than rejecting it.
 */
internal fun normalizeLinkUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.endsWith(".") || trimmed.any { it.isWhitespace() }) return null
    val withScheme =
        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    val parsed = parseWebUrl(withScheme) ?: return null
    val labels = parsed.host.split('.')
    if (labels.size < 2 || labels.any { it.isEmpty() }) return null
    if (!TLD.matches(labels.last())) return null
    return withScheme
}

/** Plain `http://` links can be tampered with in transit. */
internal fun isInsecureLink(url: String): Boolean = parseWebUrl(url)?.scheme == "http"

/** True when [url] goes through a general-purpose URL shortener, hiding its destination. */
internal fun isShortenerLink(url: String): Boolean {
    val host = parseWebUrl(url)?.host ?: return false
    return SHORTENER_DOMAINS.any { hostMatchesDomain(host, it) }
}

/**
 * General-purpose URL shorteners, ported from the official client's
 * `SHORTENER_DOMAINS`. A brand's own short links (youtu.be, amzn.to) only lead
 * to that brand, so they aren't listed.
 */
internal val SHORTENER_DOMAINS: List<String> =
    listOf(
        "aceimg.in",
        "acel.me",
        "adf.ly",
        "bit.ly",
        "bitly.com",
        "bl.ink",
        "buff.ly",
        "clck.ru",
        "cutt.ly",
        "fc-lc.xyz",
        "goo.gl",
        "goto.now",
        "is.gd",
        "link2.fm",
        "ln.run",
        "lnk.ua",
        "lnkd.in",
        "n9.cl",
        "onlylinks.cc",
        "ow.ly",
        "psee.io",
        "rb.gy",
        "rebrand.ly",
        "rg.by",
        "s.id",
        "short.do",
        "short.gy",
        "shorten.as",
        "shorten.ee",
        "shorten.is",
        "shorten.so",
        "shorten.tv",
        "shorten.world",
        "shorter.me",
        "shortn.icu",
        "shorturl.at",
        "shorturl.lt",
        "shorturl.ly",
        "slicedrve.in",
        "slnt.to",
        "smarturl.lt",
        "smarturl.my",
        "sprl.me",
        "sw.run",
        "t.co",
        "t.ly",
        "tiny.cc",
        "tinyurl.com",
        "urlr.me",
        "v.gd",
        "vdey.in",
        "videy.at",
        "videy.lt",
        "videy.my",
        "x.gd",
        "zig.ht",
    )
