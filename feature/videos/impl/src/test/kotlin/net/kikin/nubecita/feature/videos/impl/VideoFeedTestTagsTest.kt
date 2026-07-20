package net.kikin.nubecita.feature.videos.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [VideoFeedTestTags.PAGER]. The `:benchmark` module's
 * `VideoFeedScrollBenchmark` hardcodes the same literal (it deliberately does
 * not depend on `:feature:videos:impl`), so a silent rename would otherwise only
 * surface in the opt-in `run-bench` CI job. To rename: update this value AND
 * `benchmark/.../BenchmarkConstants.kt`'s `VIDEO_FEED_RES_ID` in the same PR.
 */
internal class VideoFeedTestTagsTest {
    @Test
    fun `pager tag value is pinned to video_feed`() {
        assertEquals("video_feed", VideoFeedTestTags.PAGER)
    }

    @Test
    fun `poster tag value is pinned to video_feed_poster`() {
        assertEquals("video_feed_poster", VideoFeedTestTags.POSTER)
    }
}
