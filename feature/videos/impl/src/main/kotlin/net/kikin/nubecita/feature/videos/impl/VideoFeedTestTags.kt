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
}
