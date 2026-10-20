package net.kikin.nubecita.benchmark

import android.graphics.Rect
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adaptive video comments bottom sheet & side sheet frame-timing benchmark (epic nubecita-76q8 Task 4.3).
 *
 * Measures the frame pacing and scroll jank of the comments sheet (`LazyColumn` of comments
 * and sheet detent resizing) while video playback continues seamlessly in the background.
 *
 * **Pre-resolution contract (nubecita-76q8.10):**
 * All UI navigation (Discover chip → Trending Videos carousel → vertical feed → tapping reply CTA
 * on the right rail → waiting for sheet settlement) executes strictly in `setupBlock` OUTSIDE
 * the measured loop.
 *
 * `findObject` is never repeatedly invoked within the timing loop because it triggers a synchronous
 * accessibility-hierarchy dump on the app's main thread, serializing Compose semantics trees and
 * introducing artificial frame spikes. Instead, UIAutomator references are pre-resolved in setup,
 * ensuring `FrameTimingMetric` exclusively measures real rendering performance during interactive
 * sheet gestures.
 */
@RunWith(AndroidJUnit4::class)
class VideoCommentsSheetBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun dragAndScrollCommentsSheetOverVideoPlayback() {
        var listBounds: Rect? = null
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

                // Switch to Discover feed hosting the Trending Videos carousel.
                val discoverChip =
                    device.wait(Until.findObject(By.text(FEED_DISCOVER_CHIP_TEXT)), NAV_WAIT_MS)
                        ?: throw AssertionError("Discover chip ('$FEED_DISCOVER_CHIP_TEXT') not found.")
                discoverChip.click()

                // Open vertical video feed from the first trending poster.
                device
                    .wait(Until.findObject(By.res(TRENDING_VIDEO_THUMB_RES_ID)), NAV_WAIT_MS)
                    ?.click()
                    ?: throw AssertionError(
                        "Trending carousel poster ('$TRENDING_VIDEO_THUMB_RES_ID') not found.",
                    )

                device.wait(Until.findObject(By.res(VIDEO_FEED_RES_ID)), NAV_WAIT_MS)
                    ?: throw AssertionError(
                        "Vertical feed pager ('$VIDEO_FEED_RES_ID') not found after opening poster.",
                    )

                // Locate the reply CTA on the right overlay rail and tap to open comments sheet.
                val replyCta =
                    device.wait(Until.findObject(By.res(VIDEO_FEED_RAIL_REPLY_RES_ID)), NAV_WAIT_MS)
                        ?: throw AssertionError(
                            "Reply CTA ('$VIDEO_FEED_RAIL_REPLY_RES_ID') not found on video rail.",
                        )
                replyCta.click()

                // Wait for the comments list to be composed and on-screen before timing begins.
                val commentsList =
                    device.wait(Until.findObject(By.res(VIDEO_COMMENTS_LIST_RES_ID)), NAV_WAIT_MS)
                        ?: throw AssertionError(
                            "Comments list ('$VIDEO_COMMENTS_LIST_RES_ID') did not appear after tapping reply CTA.",
                        )

                // Assert the docked comment pill is present so we never benchmark a broken layout.
                device.wait(Until.findObject(By.res(DOCKED_COMMENT_PILL_RES_ID)), NAV_WAIT_MS)
                    ?: throw AssertionError(
                        "Docked comment pill ('$DOCKED_COMMENT_PILL_RES_ID') missing in comments sheet.",
                    )

                listBounds = commentsList.visibleBounds
            },
        ) {
            val bounds =
                listBounds ?: throw AssertionError("Comments list bounds not resolved in setupBlock.")
            val centerX = bounds.centerX()
            val top = bounds.top
            val bottom = bounds.bottom
            val margin = bounds.height() / GESTURE_MARGIN_DIVISOR

            repeat(DRAG_ITERATIONS) {
                // Drag down to test sheet collapse / offset scroll, then up to expand
                device.swipe(centerX, top + margin, centerX, bottom - margin, 20)
                device.waitForIdle()
                device.swipe(centerX, bottom - margin, centerX, top + margin, 20)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val NAV_WAIT_MS: Long = 10_000
        const val DRAG_ITERATIONS: Int = 4
        const val GESTURE_MARGIN_DIVISOR: Int = 6
    }
}
