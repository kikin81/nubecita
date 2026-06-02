package net.kikin.nubecita.core.feedmapping

/**
 * Detection + sizing for GIFs posted as `app.bsky.embed.external`. Bluesky's
 * GIF picker (now Klipy; historically Tenor/Giphy) publishes the GIF as an
 * external embed whose `uri` is an `image/gif`. We render those inline and
 * animated (Coil's AnimatedImageDecoder) rather than as a static link card.
 * See nubecita-q01y.
 */

private val GIF_EXACT_HOSTS = setOf("static.klipy.com", "media.tenor.com")

/**
 * True when [uri] is an animated GIF external embed: a known GIF-provider host
 * (Klipy / Tenor / any `*.giphy.com`), or a path that ends in `.gif`.
 */
internal fun isGifExternalUri(uri: String): Boolean {
    val host = uriHost(uri)
    if (host != null && (host in GIF_EXACT_HOSTS || host.endsWith(".giphy.com"))) return true
    return uri.substringBefore('?').endsWith(".gif", ignoreCase = true)
}

/**
 * width / height parsed from the `ww` / `hh` query params (Klipy and Tenor URLs
 * carry them), or null when absent / unparseable — the render then caps height
 * instead of guessing an aspect ratio.
 */
internal fun gifAspectRatioOrNull(uri: String): Float? {
    val query = uri.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null
    val params =
        query
            .split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                val value = pair.substringAfter('=', "")
                if (key.isNotEmpty() && value.isNotEmpty()) key to value else null
            }.toMap()
    val width = params["ww"]?.toFloatOrNull()
    val height = params["hh"]?.toFloatOrNull()
    return if (width != null && height != null && width > 0f && height > 0f) width / height else null
}

private fun uriHost(uri: String): String? =
    runCatching {
        java.net
            .URI(uri)
            .host
            ?.lowercase()
    }.getOrNull()
