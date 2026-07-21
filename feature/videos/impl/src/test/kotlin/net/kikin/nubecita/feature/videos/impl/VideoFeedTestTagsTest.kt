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
    fun `chrome tag values are pinned`() {
        assertEquals("video_feed_like", VideoFeedTestTags.RAIL_LIKE)
        assertEquals("video_feed_repost", VideoFeedTestTags.RAIL_REPOST)
        assertEquals("video_feed_reply", VideoFeedTestTags.RAIL_REPLY)
        assertEquals("video_feed_share", VideoFeedTestTags.RAIL_SHARE)
        assertEquals("video_feed_mute", VideoFeedTestTags.MUTE)
        assertEquals("video_feed_caption", VideoFeedTestTags.CAPTION)
        assertEquals("video_feed_pause_indicator", VideoFeedTestTags.PAUSE_INDICATOR)
    }

    @Test
    fun `overflow and bookmark tag values are pinned`() {
        assertEquals("video_feed_bookmark", VideoFeedTestTags.RAIL_BOOKMARK)
        assertEquals("video_feed_overflow", VideoFeedTestTags.RAIL_OVERFLOW)
    }

    @Test
    fun `poster tag value is pinned to video_feed_poster`() {
        assertEquals("video_feed_poster", VideoFeedTestTags.POSTER)
    }
}
