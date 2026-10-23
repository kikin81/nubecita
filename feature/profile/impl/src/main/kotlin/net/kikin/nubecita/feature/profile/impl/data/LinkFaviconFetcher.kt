package net.kikin.nubecita.feature.profile.impl.data

/**
 * Fetches a site's favicon for a profile link. Returns PNG bytes, or null when
 * the site has no usable icon — a normal outcome (slow site, no icon, wrong
 * type, too big) that never blocks adding the link; the pill falls back to the
 * globe glyph. Upload happens later, during Save.
 */
internal interface LinkFaviconFetcher {
    suspend fun fetch(url: String): ByteArray?
}
