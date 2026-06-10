package net.kikin.nubecita.feature.widgets.impl.model

import kotlinx.datetime.TimeZone
import net.kikin.nubecita.core.common.time.RelativeTimeStrings
import net.kikin.nubecita.core.common.time.formatRelativeTime
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.feature.widgets.impl.image.widgetImageCount
import net.kikin.nubecita.feature.widgets.impl.image.widgetMediaDescription
import net.kikin.nubecita.feature.widgets.impl.image.widgetThumbnailUrl
import kotlin.time.Instant

/**
 * The flat, render-ready projection of a [PostUi] for a Glance widget row
 * (D-C8). Glance is Compose-runtime, not Compose-UI, so the widget can't reuse
 * `PostCard`; this is the minimal shape a compact row needs. Pure data — the
 * actual thumbnail [java.io.File] is resolved at render time from
 * [net.kikin.nubecita.feature.widgets.impl.image.WidgetThumbnailStore] keyed by
 * [postUri].
 */
internal data class WidgetPostItem(
    /** Post AT-URI — the deep-link target and the thumbnail cache key. */
    val postUri: String,
    /** Display name, falling back to the handle when blank. */
    val authorDisplay: String,
    val handle: String,
    /** Whitespace-collapsed, length-capped post text. */
    val text: String,
    /** Compact relative time (e.g. "2h", "3d", or a date for old posts). */
    val relativeTime: String,
    /** Whether the post has a thumbnail to show (drives the media slot). */
    val hasMedia: Boolean,
    /** "+N" overflow-badge value (`0` = no badge). */
    val extraImageCount: Int,
    /** Media accessibility description, or `null` for a text-only post. */
    val mediaContentDescription: String?,
)

/**
 * Project a cached [PostUi] to a [WidgetPostItem], anchoring the relative time
 * to [now]. [strings]/[timeZone] are injectable for testing; the MVP widget
 * surface is English-only.
 */
internal fun PostUi.toWidgetItem(
    now: Instant,
    strings: RelativeTimeStrings = RelativeTimeStrings.English,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): WidgetPostItem {
    val imageCount = widgetImageCount(embed)
    return WidgetPostItem(
        postUri = id,
        authorDisplay = author.displayName.ifBlank { author.handle },
        handle = author.handle,
        text = snippet(text),
        relativeTime = formatRelativeTime(now = now, then = createdAt, strings = strings, timeZone = timeZone),
        hasMedia = widgetThumbnailUrl(embed) != null,
        extraImageCount = (imageCount - 1).coerceAtLeast(0),
        mediaContentDescription = widgetMediaDescription(embed),
    )
}

/** Collapse whitespace to single spaces and cap length for a compact widget row. */
private fun snippet(text: String): String {
    val oneLine = text.replace(WHITESPACE, " ").trim()
    return if (oneLine.length <= MAX_SNIPPET_CHARS) {
        oneLine
    } else {
        oneLine.take(MAX_SNIPPET_CHARS - 1).trimEnd() + "…"
    }
}

private const val MAX_SNIPPET_CHARS = 200
private val WHITESPACE = Regex("\\s+")
