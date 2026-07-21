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

    @Test
    fun `surface ratio comes from the settled item, not the lagging decoded size`() {
        // The aspect-lag bug: the surface was sized from the active player's decoded
        // videoSizeDp, which lags the pager's settledPage during a swipe. So a
        // landscape->portrait swipe briefly sized the surface 16:9 for the new
        // portrait page. The surface must track the settled ITEM's declared ratio.
        assertEquals(0.5625f, videoFeedSurfaceAspectRatio(9f / 16f), 0.0001f)
        assertEquals(16f / 9f, videoFeedSurfaceAspectRatio(16f / 9f), 0.0001f)
    }

    @Test
    fun `surface ratio falls back to portrait for a null or non-positive ratio`() {
        // A 0f/negative ratio would crash Modifier.aspectRatio (requires > 0), so the
        // render boundary drops it to the portrait default even though the mapper
        // already guards the source.
        assertEquals(9f / 16f, videoFeedSurfaceAspectRatio(null), 0.0001f)
        assertEquals(9f / 16f, videoFeedSurfaceAspectRatio(0f), 0.0001f)
        assertEquals(9f / 16f, videoFeedSurfaceAspectRatio(-1.5f), 0.0001f)
    }

    @Test
    fun `progress is zero before the player is prepared`() {
        // Player.duration is TIME_UNSET (-1) until prepared; a naive divide would
        // yield a negative fraction. It must read as an empty bar, not a full one.
        assertEquals(0f, progressFraction(positionMs = 0L, durationMs = -1L), 0.0001f)
        assertEquals(0f, progressFraction(positionMs = 0L, durationMs = 0L), 0.0001f)
    }

    @Test
    fun `progress is the position over duration mid clip`() {
        assertEquals(0.5f, progressFraction(positionMs = 5_000L, durationMs = 10_000L), 0.0001f)
        assertEquals(0.25f, progressFraction(positionMs = 3_750L, durationMs = 15_000L), 0.0001f)
    }

    @Test
    fun `progress is full at the end and never overruns`() {
        assertEquals(1f, progressFraction(positionMs = 10_000L, durationMs = 10_000L), 0.0001f)
        // A position transiently past duration at a loop boundary must clamp to 1.
        assertEquals(1f, progressFraction(positionMs = 11_000L, durationMs = 10_000L), 0.0001f)
    }
}
