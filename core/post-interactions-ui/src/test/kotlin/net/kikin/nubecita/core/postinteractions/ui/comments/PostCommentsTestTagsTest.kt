package net.kikin.nubecita.core.postinteractions.ui.comments

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [PostCommentsTestTags.COMMENTS_LIST] and [PostCommentsTestTags.DOCKED_PILL].
 * The `:benchmark` module's `PostCommentsSheetBenchmark` hardcodes both literals
 * (it deliberately does not depend on `:core:post-interactions-ui`), so a silent
 * rename would otherwise only surface in the opt-in `run-bench` CI job.
 */
class PostCommentsTestTagsTest {
    @Test
    fun `comments list tag value is pinned to post_comments_list`() {
        assertEquals("post_comments_list", PostCommentsTestTags.COMMENTS_LIST)
    }

    @Test
    fun `docked pill tag value is pinned to docked_comment_pill`() {
        assertEquals("docked_comment_pill", PostCommentsTestTags.DOCKED_PILL)
    }

    @Test
    fun `retry button tag value is pinned to post_comments_retry`() {
        assertEquals("post_comments_retry", PostCommentsTestTags.RETRY_BUTTON)
    }
}
