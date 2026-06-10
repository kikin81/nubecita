package net.kikin.nubecita.feature.widgets.impl.image

import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.thumbOrFullsize

/**
 * The single image a widget pre-decodes for a post, or `null` for a text-only
 * post (D-C5). Pure (no Android / Coil), so the per-embed decision is
 * JVM-unit-testable in isolation from the decode + file I/O.
 *
 * Rules:
 * - **Images** → the FIRST image's thumbnail (`thumbOrFullsize`). The "+N"
 *   overflow count is derived separately at render time from the same embed —
 *   no extra decode.
 * - **Video** → the poster, when present.
 * - **RecordWithMedia** → the post's own media (a quote *with* attached
 *   media); recurse into the media half only.
 * - **External / Gif / Record / RecordUnavailable / Unsupported / Empty** →
 *   `null`: quote-post and link previews render text-only for the MVP, and the
 *   nested/extra decodes aren't worth the RemoteViews IPC budget.
 * - Any media carrying a [net.kikin.nubecita.data.models.MediaContentWarning]
 *   is skipped — a home-screen widget must not surface a flagged thumbnail.
 */
internal fun widgetThumbnailUrl(embed: EmbedUi): String? =
    when (embed) {
        is EmbedUi.Images ->
            if (embed.contentWarning != null) {
                null
            } else {
                embed.items.firstOrNull()?.thumbOrFullsize()
            }

        is EmbedUi.Video ->
            if (embed.contentWarning != null) null else embed.posterUrl

        is EmbedUi.RecordWithMedia -> widgetThumbnailUrl(embed.media)

        is EmbedUi.External,
        is EmbedUi.Gif,
        is EmbedUi.Record,
        is EmbedUi.RecordUnavailable,
        is EmbedUi.Unsupported,
        EmbedUi.Empty,
        -> null
    }

/**
 * Number of images the post carries — drives the "+N" overflow badge (the badge
 * shows `count - 1`). Counts images only; a video is a single poster (no badge),
 * everything else is `0`. Mirrors [widgetThumbnailUrl]'s reach into
 * `RecordWithMedia`.
 */
internal fun widgetImageCount(embed: EmbedUi): Int =
    when (embed) {
        is EmbedUi.Images -> embed.items.size
        is EmbedUi.RecordWithMedia -> widgetImageCount(embed.media)
        is EmbedUi.Video,
        is EmbedUi.External,
        is EmbedUi.Gif,
        is EmbedUi.Record,
        is EmbedUi.RecordUnavailable,
        is EmbedUi.Unsupported,
        EmbedUi.Empty,
        -> 0
    }

/**
 * Accessibility description for the post's media thumbnail, or `null` when the
 * post renders text-only in the widget. English-only for the MVP (the widget
 * surface isn't yet localized). Skips content-warned media, matching
 * [widgetThumbnailUrl].
 */
internal fun widgetMediaDescription(embed: EmbedUi): String? =
    when (embed) {
        is EmbedUi.Images ->
            when {
                embed.contentWarning != null -> null
                embed.items.size > 1 -> "Image, ${embed.items.size} total"
                embed.items.isNotEmpty() -> "Image"
                else -> null
            }

        is EmbedUi.Video ->
            if (embed.contentWarning == null && embed.posterUrl != null) "Video" else null

        is EmbedUi.RecordWithMedia -> widgetMediaDescription(embed.media)

        is EmbedUi.External,
        is EmbedUi.Gif,
        is EmbedUi.Record,
        is EmbedUi.RecordUnavailable,
        is EmbedUi.Unsupported,
        EmbedUi.Empty,
        -> null
    }
