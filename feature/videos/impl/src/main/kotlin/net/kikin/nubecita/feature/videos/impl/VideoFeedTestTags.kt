package net.kikin.nubecita.feature.videos.impl

/**
 * Stable Compose `testTag` constants for `:feature:videos:impl` — the seam
 * between the vertical video feed and the `:benchmark` Macrobenchmark module.
 *
 * `:benchmark` does not depend on this module (it's intentionally isolated from
 * production classpaths), so the contract is the tag *value*, not this object.
 * Renaming [PAGER] is a coordinated change: update the string here AND
 * `benchmark/src/main/kotlin/.../BenchmarkConstants.kt`'s `VIDEO_FEED_RES_ID`.
 * `VideoFeedTestTagsTest` pins the value so a silent rename fails in unit tests
 * rather than only in the opt-in `run-bench` CI job. The host enables
 * `testTagsAsResourceId = true` in `MainActivity`, so UIAutomator selects via
 * the single-arg `By.res("<value>")`.
 */
object VideoFeedTestTags {
    /** The full-screen `VerticalPager` that owns the swipe gesture. */
    const val PAGER: String = "video_feed"

    /** A single page's poster layer, which covers the video until its first frame. */
    const val POSTER: String = "video_feed_poster"

    /** Right-rail like cell. */
    const val RAIL_LIKE: String = "video_feed_like"

    /** Right-rail repost cell. */
    const val RAIL_REPOST: String = "video_feed_repost"

    /** Right-rail reply cell. */
    const val RAIL_REPLY: String = "video_feed_reply"

    /** Right-rail share cell. */
    const val RAIL_SHARE: String = "video_feed_share"

    /** Mute toggle at the foot of the rail. */
    const val MUTE: String = "video_feed_mute"

    /** Caption block; tap toggles expansion. */
    const val CAPTION: String = "video_feed_caption"

    /** Centre glyph shown while playback is paused. */
    const val PAUSE_INDICATOR: String = "video_feed_pause_indicator"

    /** Right-rail bookmark toggle cell. */
    const val RAIL_BOOKMARK: String = "video_feed_bookmark"

    /** Right-rail overflow trigger cell. */
    const val RAIL_OVERFLOW: String = "video_feed_overflow"

    /** Read-only playback progress bar at the foot of the feed. */
    const val PROGRESS_BAR: String = "video_feed_progress"
}
