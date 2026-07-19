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
 * Vertical-video-feed frame-timing benchmark (epic nubecita-zdv8 Slice 5c).
 * Measures the swipe-through jank of the full-screen `VerticalPager` — the
 * surface where the pooled player promotes a new clip on every settle, so it's
 * the stress case for the 120 Hz target.
 *
 * The navigation (Discover chip → Trending Videos carousel → the vertical feed)
 * runs in `setupBlock`, OUTSIDE measurement — only the pager flings are timed,
 * so the metric is pure feed-scroll jank, not one-off navigation cost.
 *
 * Metrics: `FrameTimingMetric` → `frameDurationCpuMs` / `frameOverrunMs`
 * distributions (95th percentile is the practical regression target). Same
 * `CompilationMode.None` caveat as `FeedScrollBenchmark`.
 *
 * Selector note (identical contract to `FeedScrollBenchmark`): Compose's
 * `testTagsAsResourceId = true` surfaces tags as bare `resource-id` values, so
 * the single-arg `By.res(id)` matches. The literals live in
 * `BenchmarkConstants` and are pinned to the production tags by
 * `VideoFeedTestTagsTest` / `FeedTestTagsTest`.
 */
@RunWith(AndroidJUnit4::class)
class VideoFeedScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollVideoFeed() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()

                // Feed must be loaded before its chips exist.
                device.wait(Until.findObject(By.res(FEED_LIST_RES_ID)), NAV_WAIT_MS)
                    ?: throw AssertionError(
                        "Feed list ('$FEED_LIST_RES_ID') not found — is the bench build signed in?",
                    )
                // Switch to the Discover feed, which hosts the Trending Videos carousel.
                // Wait (not findObject) — the chip may not be laid out the instant the
                // feed list appears; a bare findObject would flake.
                val discoverChip =
                    device.wait(Until.findObject(By.text(FEED_DISCOVER_CHIP_TEXT)), NAV_WAIT_MS)
                        ?: throw AssertionError("Discover chip ('$FEED_DISCOVER_CHIP_TEXT') not found.")
                discoverChip.click()
                // Open the vertical feed at the first trending poster.
                device
                    .wait(Until.findObject(By.res(TRENDING_VIDEO_THUMB_RES_ID)), NAV_WAIT_MS)
                    ?.click()
                    ?: throw AssertionError(
                        "Trending carousel poster ('$TRENDING_VIDEO_THUMB_RES_ID') not found.",
                    )
                device.wait(Until.findObject(By.res(VIDEO_FEED_RES_ID)), NAV_WAIT_MS)
                    ?: throw AssertionError(
                        "Vertical feed pager ('$VIDEO_FEED_RES_ID') not found after opening a poster.",
                    )
            },
        ) {
            val pager =
                device.findObject(By.res(VIDEO_FEED_RES_ID))
                    ?: throw AssertionError("Vertical feed pager ('$VIDEO_FEED_RES_ID') vanished before scroll.")
            // Shrink the active swipe area away from the edges so a fling doesn't
            // trip a system back/home gesture.
            pager.setGestureMargin(pager.visibleBounds.width() / GESTURE_MARGIN_DIVISOR)
            repeat(SCROLL_ITERATIONS) {
                pager.fling(Direction.UP)
                device.waitForIdle()
            }
        }

    private companion object {
        const val NAV_WAIT_MS: Long = 10_000
        const val SCROLL_ITERATIONS: Int = 5
        const val GESTURE_MARGIN_DIVISOR: Int = 5
    }
}
