package net.kikin.nubecita.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feed-scroll frame-timing benchmark. Cold-launches the app, waits
 * for the loaded feed `LazyColumn` to appear (selected via the
 * `feed_list` testTag that `:feature:feed:impl` exposes), then
 * performs a deterministic scroll gesture under measurement.
 *
 * Metrics: `FrameTimingMetric` produces `frameDurationCpuMs` and
 * `frameOverrunMs` distributions (50th / 95th / 99th percentile).
 * The 95th is the practical regression target — single-digit
 * outliers are inevitable on emulated hardware.
 *
 * Compilation mode is fixed to `None` (matches StartupBenchmark) for
 * now. The follow-up ticket that lands a baseline profile
 * (`nubecita-crmi.2`) extends both benches to parameterize over
 * `CompilationMode.Partial`.
 *
 * Resource-id selector: macrobench is a separate APK from `:app`,
 * and the macrobench module deliberately doesn't depend on
 * `:feature:feed:impl` (the production module). The selector below
 * uses a HARDCODED literal — kept in sync with
 * `FeedTestTags.LIST` via the unit test `FeedTestTagsTest` so a
 * silent rename surfaces in fast unit-test runs instead of waiting
 * for a `run-bench`-labeled CI build.
 */
@RunWith(AndroidJUnit4::class)
class FeedScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeed() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            // Wait for the loaded feed list to materialize. A null
            // result here means either the tag is missing, the user
            // isn't signed in (login screen rendered instead), or the
            // feed never loaded. Fail fast with a message that points
            // at the testTag contract — silently producing a zero-frame
            // trace would otherwise pass the bench with meaningless
            // numbers.
            //
            // Selector note: Compose's `testTagsAsResourceId = true`
            // surfaces tags as bare `resource-id` values with no
            // package qualifier (verified via `uiautomator dump`).
            // Use the single-arg `By.res(id)` — the two-arg form
            // builds `packageName:id/<id>`, which Compose tags do not
            // produce, and would silently never match.
            val feedList =
                device.wait(
                    Until.findObject(By.res(FEED_LIST_RES_ID)),
                    FEED_LIST_WAIT_MS,
                ) ?: error(
                    "FeedScreen's LazyColumn (res id '$FEED_LIST_RES_ID') was not " +
                        "found within ${FEED_LIST_WAIT_MS}ms — verify FeedTestTags.LIST is " +
                        "still applied to the loaded-feed LazyColumn and that the " +
                        "testTagsAsResourceId root semantics flag is enabled.",
                )

            // Deterministic gesture profile: five upward swipes at 80%
            // of the list's vertical extent, each with a fixed
            // step-count -> velocity. Velocity is set via gesture-margin
            // (smaller margin => longer travel => higher velocity);
            // the default ~ 250ms-per-swipe is the AndroidX-canonical
            // value for feed-scroll benchmarks.
            feedList.setGestureMargin(feedList.visibleBounds.width() / GESTURE_MARGIN_DIVISOR)
            repeat(SCROLL_ITERATIONS) {
                feedList.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }

    private companion object {
        const val FEED_LIST_RES_ID: String = "feed_list"
        const val FEED_LIST_WAIT_MS: Long = 10_000
        const val SCROLL_ITERATIONS: Int = 5
        const val GESTURE_MARGIN_DIVISOR: Int = 5
    }
}
