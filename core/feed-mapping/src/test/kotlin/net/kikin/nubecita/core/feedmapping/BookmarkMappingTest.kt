package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.ViewerState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bookmark viewer-state mapping (nubecita-i8ny.2). The bookmark overlay adds
 * `app.bsky.feed.defs#viewerState.bookmarked`; we project it onto
 * [net.kikin.nubecita.data.models.ViewerStateUi.isBookmarked]. Absent must map
 * to `false` (not bookmarked) — the common case for any post the viewer hasn't
 * bookmarked or when the overlay is absent.
 */
internal class BookmarkMappingTest {
    @Test
    fun `bookmarked true maps to isBookmarked true`() {
        val ui = ViewerState(bookmarked = true).toViewerStateUi()
        assertTrue(ui.isBookmarked)
    }

    @Test
    fun `bookmarked false maps to isBookmarked false`() {
        val ui = ViewerState(bookmarked = false).toViewerStateUi()
        assertFalse(ui.isBookmarked)
    }

    @Test
    fun `absent bookmarked maps to isBookmarked false`() {
        val ui = ViewerState().toViewerStateUi()
        assertFalse(ui.isBookmarked, "an absent bookmark flag must not report the post as bookmarked")
    }

    @Test
    fun `a null post-viewer maps to isBookmarked false`() {
        val ui = (null as ViewerState?).toViewerStateUi()
        assertFalse(ui.isBookmarked)
    }
}
