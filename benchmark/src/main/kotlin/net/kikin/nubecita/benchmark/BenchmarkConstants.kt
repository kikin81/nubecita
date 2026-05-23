package net.kikin.nubecita.benchmark

/**
 * Package name of the target APK. Hardcoded — Macrobench requires a
 * literal, and there's no other consumer of this constant so reading
 * it from BuildConfig would only add ceremony.
 */
internal const val TARGET_PACKAGE: String = "net.kikin.nubecita"

/**
 * Default iteration count for every benchmark. AndroidX's own default
 * is 5; keeping the value here so all benchmark classes share one
 * knob — bumping to 10 (for variance reduction) is a one-line change.
 */
internal const val DEFAULT_ITERATIONS: Int = 5

/**
 * Resource-id selector that surfaces FeedScreen's `LazyColumn` to
 * UIAutomator. Compose's `testTagsAsResourceId = true` (root semantics
 * flag on `MainActivity`) exposes the `feed_list` Compose `testTag` as
 * a bare `resource-id` value with no package qualifier — so the
 * single-arg `By.res("feed_list")` form matches and the two-arg
 * `By.res(packageName, id)` form (which builds `packageName:id/<id>`)
 * silently never matches. The string is the contract between this
 * module and `:feature:feed:impl`, intentionally hardcoded — the
 * macrobench module deliberately doesn't depend on the production
 * module — and pinned by `:feature:feed:impl/FeedTestTagsTest` so a
 * silent rename surfaces in fast unit-test runs.
 *
 * Single source of truth shared by `BaselineProfileGenerator` (waits
 * for it during profile capture) and `FeedScrollBenchmark` (waits for
 * it before flinging).
 */
internal const val FEED_LIST_RES_ID: String = "feed_list"
