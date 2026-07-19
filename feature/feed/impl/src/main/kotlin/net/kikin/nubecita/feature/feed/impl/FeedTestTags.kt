package net.kikin.nubecita.feature.feed.impl

/**
 * Stable Compose `testTag` constants for `:feature:feed:impl`.
 *
 * These tags are the seam between the feed screen and the
 * `:benchmark` Macrobenchmark module. `:benchmark` does not depend on
 * `:feature:feed:impl` (the macrobench module is intentionally
 * isolated from production module classpaths), so the contract is the
 * tag *value* — not this object. Renaming or changing [LIST] is a
 * coordinated change: update the string here AND in
 * `benchmark/src/main/kotlin/.../FeedScrollBenchmark.kt`.
 *
 * The host that wraps `FeedScreen` must enable
 * `testTagsAsResourceId = true` somewhere up the tree so UIAutomator
 * can select tagged nodes via the **single-arg** `By.res("<tag value>")`.
 * Compose surfaces tags as bare `resource-id` values (no package
 * qualifier), so the two-arg `By.res(packageName, id)` form silently
 * never matches. In nubecita, the flag is enabled in `MainActivity`'s
 * `setContent` root semantics modifier.
 */
object FeedTestTags {
    /**
     * The top-level `LazyColumn` rendering the loaded feed items.
     * Applied inside `LoadedFeedContent` (the only branch where the
     * scrollable list actually exists — `InitialLoading` renders a
     * shimmer `LazyColumn`, but the bench drives the loaded variant
     * since the metric of interest is real-content frame timing).
     */
    const val LIST: String = "feed_list"

    /**
     * A poster in the Discover "Trending Videos" carousel. Applied to every
     * thumbnail (they share the tag); the `:benchmark` `VideoFeedScrollBenchmark`
     * selects the first via `By.res` to open the vertical video feed. Coordinated
     * with `benchmark/.../BenchmarkConstants.kt`'s `TRENDING_VIDEO_THUMB_RES_ID`.
     */
    const val TRENDING_VIDEO_THUMB: String = "trending_video_thumb"
}
