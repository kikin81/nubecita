package net.kikin.nubecita.data.models

/**
 * Assembles the `app.bsky.embed.external` URI for posting a picked KLIPY item.
 *
 * The shape is the interop contract: the `static.klipy.com/ii/…` gif URL plus
 * `ww`/`hh` pixel dimensions (which the render side's `gifAspectRatioOrNull`
 * reads back) and, when available, an `mp4` slug for GIF-aware clients that
 * play the video variant. GIF-detecting clients — Nubecita's own feed
 * (`isGifExternalUri`) and the official Bluesky app — key on this URL, so a
 * post built here animates inline across the ecosystem.
 *
 * `mp4` is a best-effort slug derived from [mp4Url]'s filename; our own
 * rendering only needs the host + `ww`/`hh`, so an absent/imperfect slug
 * degrades to a still-animating gif rather than breaking playback.
 */
public fun KlipyMediaUi.toExternalEmbedUri(): String {
    val params =
        buildList {
            add("ww=$embedWidth")
            add("hh=$embedHeight")
            mp4Url?.klipyFileSlug()?.let { add("mp4=$it") }
        }
    val separator = if (embedUrl.contains('?')) "&" else "?"
    return embedUrl + separator + params.joinToString("&")
}

/** `https://static.klipy.com/ii/a/b/cat.mp4?v=1.0` → `cat`; null if empty. */
private fun String.klipyFileSlug(): String? =
    substringBefore('?')
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .ifBlank { null }
