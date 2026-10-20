package net.kikin.nubecita.core.postinteractions.ui.comments

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [VideoCommentsTestTags.COMMENTS_LIST] and [VideoCommentsTestTags.DOCKED_PILL].
 * The `:benchmark` module's `VideoCommentsSheetBenchmark` hardcodes both literals
 * (it deliberately does not depend on `:core:post-interactions-ui`), so a silent
 * rename would otherwise only surface in the opt-in `run-bench` CI job.
 */
class VideoCommentsTestTagsTest {
    @Test
    fun `comments list tag value is pinned to video_comments_list`() {
        assertEquals("video_comments_list", VideoCommentsTestTags.COMMENTS_LIST)
    }

    @Test
    fun `docked pill tag value is pinned to docked_comment_pill`() {
        assertEquals("docked_comment_pill", VideoCommentsTestTags.DOCKED_PILL)
    }

    @Test
    fun `retry button tag value is pinned to video_comments_retry`() {
        assertEquals("video_comments_retry", VideoCommentsTestTags.RETRY_BUTTON)
    }
}
