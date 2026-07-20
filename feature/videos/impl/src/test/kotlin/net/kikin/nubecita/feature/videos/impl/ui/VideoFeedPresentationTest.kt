package net.kikin.nubecita.feature.videos.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VideoFeedPresentationTest {
    @Test
    fun `settled surface is not translated`() {
        val translation =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = 0f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(0f, translation)
    }

    @Test
    fun `dragging toward the next page moves the surface up`() {
        // Scroll position 3.5 with the loaded page at 3: the video is half a
        // page above centre, i.e. negative translationY.
        val translation =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = 0.5f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(-1000f, translation)
    }

    @Test
    fun `dragging toward the previous page moves the surface down`() {
        val translation =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = -0.5f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(1000f, translation)
    }

    @Test
    fun `translation stays continuous when currentPage flips past settledPage`() {
        // The regression this function exists to prevent. Past the halfway point
        // the pager reports currentPage = 4 with a negative offset rather than
        // currentPage = 3 with a large positive one. Measuring against
        // settledPage keeps the value continuous; measuring against currentPage
        // would snap the video a whole page sideways mid-drag.
        val justBefore =
            surfaceTranslationPx(
                currentPage = 3,
                currentPageOffsetFraction = 0.49f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        val justAfter =
            surfaceTranslationPx(
                currentPage = 4,
                currentPageOffsetFraction = -0.49f,
                settledPage = 3,
                pageHeightPx = 2000f,
            )
        assertEquals(-980f, justBefore)
        assertEquals(-1020f, justAfter)
    }

    @Test
    fun `poster hides only on the settled page once the first frame has rendered`() {
        assertEquals(0f, posterAlphaTarget(isSettledPage = true, coverSurface = false))
    }

    @Test
    fun `poster covers the settled page until the first frame renders`() {
        assertEquals(1f, posterAlphaTarget(isSettledPage = true, coverSurface = true))
    }

    @Test
    fun `poster always covers a non-settled page`() {
        // Neighbours have no player bound, so their poster is the only content
        // they ever show — this is what keeps a cold page from flashing black.
        assertEquals(1f, posterAlphaTarget(isSettledPage = false, coverSurface = false))
        assertEquals(1f, posterAlphaTarget(isSettledPage = false, coverSurface = true))
    }
}
