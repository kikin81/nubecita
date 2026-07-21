package net.kikin.nubecita.feature.videos.impl.ui

import net.kikin.nubecita.feature.videos.impl.DEFAULT_VIDEO_ASPECT_RATIO

/**
 * Vertical offset, in pixels, to apply to the single persistent video surface so
 * it slides with the page it belongs to.
 *
 * The surface is measured against [settledPage] — the page whose player is
 * actually bound — and NOT against `currentPage`, which flips to the next page
 * at the halfway point of a drag and would snap the video a full page
 * vertically mid-gesture.
 *
 * Negative moves the surface up (dragging toward later pages).
 */
internal fun surfaceTranslationPx(
    currentPage: Int,
    currentPageOffsetFraction: Float,
    settledPage: Int,
    pageHeightPx: Float,
): Float {
    val scrollPosition = currentPage + currentPageOffsetFraction
    return (settledPage - scrollPosition) * pageHeightPx
}

/**
 * Target alpha for a page's poster.
 *
 * The poster renders *over* the video (the pager sits above the surface), so it
 * starts opaque and fades out to reveal the frame. Only the settled page has a
 * player bound, so every other page keeps its poster at full opacity — that is
 * what stops a cold page from showing black.
 */
internal fun posterAlphaTarget(
    isSettledPage: Boolean,
    coverSurface: Boolean,
): Float = if (isSettledPage && !coverSurface) 0f else 1f

/**
 * The aspect ratio to size a video page's surface and poster to.
 *
 * Derived ONLY from the item's declared ratio ([itemAspectRatio]), never from the
 * active player's decoded `videoSizeDp`. The decoded size is tied to `activePlayer`,
 * which LAGS the pager's `settledPage` during a swipe: for the gap between the page
 * settling and the pool promoting the next player, the decoded size is still the
 * OUTGOING clip's. Preferring it sized the surface to the previous video's aspect
 * ratio and squished the new one for a frame — e.g. a 16:9 → 9:16 swipe briefly
 * showed the portrait clip letterboxed into a landscape box, then snapped. The
 * declared ratio always corresponds to its own page, and both surface and poster
 * flow through this one function, so they never disagree.
 *
 * Falls back to [DEFAULT_VIDEO_ASPECT_RATIO] for a null OR non-positive input: a
 * `0f`/negative ratio would crash `Modifier.aspectRatio` (which requires a strictly
 * positive value). The mapper already guards this at the source, so this is the
 * last line of defense for the render boundary.
 *
 * (Bluesky video embeds carry an accurate `aspectRatio` from upload, so dropping
 * the decoded refinement costs nothing in practice.)
 */
internal fun videoFeedSurfaceAspectRatio(itemAspectRatio: Float?): Float = itemAspectRatio?.takeIf { it > 0f } ?: DEFAULT_VIDEO_ASPECT_RATIO
