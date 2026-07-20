package net.kikin.nubecita.feature.videos.impl.ui

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
