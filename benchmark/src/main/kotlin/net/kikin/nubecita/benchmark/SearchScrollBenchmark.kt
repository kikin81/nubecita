package net.kikin.nubecita.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for the three scrolling surfaces Search added: the Posts results
 * list, and (wide windows only) the Explore and Summary second-pane lists.
 *
 * Same rule, metric, compilation mode, iteration count and warm start as
 * [FeedScrollBenchmark], but not the same gesture profile, so the two are not
 * like-for-like (the tails especially): the bench search fixture is one page of
 * 10 posts, so this test flings both directions and reverses at the list ends,
 * while the feed test flings one way through a long paged list. Navigation and
 * typing happen in `setupBlock`; the measured block is only the flings. The list tags are the contract in
 * `BenchmarkConstants` (pinned by `:feature:search:impl/SearchTestTagsTest`).
 *
 * The Explore and Summary lists exist only in the list-detail second pane, so a
 * phone never shows them. Those two tests skip (JUnit `Assume`) when their list
 * does not appear, rather than failing on a window that cannot have it.
 *
 * Meant for the bench build: the offline fake search returns the same fixed
 * posts for every query, so the numbers measure scrolling, not the network.
 */
@RunWith(AndroidJUnit4::class)
class SearchScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollSearchResults() = measureListScroll(listResId = SEARCH_POSTS_LIST_RES_ID, submitQuery = true, required = true)

    @Test
    fun scrollExplorePane() = measureListScroll(listResId = SEARCH_EXPLORE_LIST_RES_ID, submitQuery = false, required = false)

    @Test
    fun scrollSummaryPane() = measureListScroll(listResId = SEARCH_SUMMARY_LIST_RES_ID, submitQuery = true, required = false)

    private fun measureListScroll(
        listResId: String,
        submitQuery: Boolean,
        required: Boolean,
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openSearch(submitQuery)
            val present = device.wait(Until.hasObject(By.res(listResId)), LIST_WAIT_MS)
            if (required) {
                check(present) {
                    "Search list (res id '$listResId') was not found within ${LIST_WAIT_MS}ms — " +
                        "verify SearchTestTags is still applied and the app is signed in / the bench build."
                }
            } else {
                assumeTrue("'$listResId' is not shown here (needs a wide window with a second pane)", present)
            }
        },
    ) {
        val list = device.findObject(By.res(listResId)) ?: error("list '$listResId' disappeared before the fling")
        list.setGestureMargin(list.visibleBounds.width() / GESTURE_MARGIN_DIVISOR)
        repeat(FLING_ROUNDS) {
            list.fling(Direction.UP)
            device.waitForIdle()
        }
        repeat(FLING_ROUNDS + 1) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }

    /**
     * Select the Search tab and, when [submitQuery], make sure a query is submitted
     * (the warm app keeps the previous iteration's results, so it is typed only
     * when the results are not already up).
     */
    private fun MacrobenchmarkScope.openSearch(submitQuery: Boolean) {
        val tab = device.wait(Until.findObject(By.desc(TAB_SEARCH_DESC)), NAV_WAIT_MS)
        checkNotNull(tab) { "Search tab not found" }.click()
        device.waitForIdle()
        if (!submitQuery || device.hasObject(By.res(SEARCH_POSTS_LIST_RES_ID))) return
        val input = device.wait(Until.findObject(By.res(SEARCH_INPUT_RES_ID)), NAV_WAIT_MS)
        checkNotNull(input) { "Search input not found" }.click()
        // The tap expands the bar into its own window, where the tag does not
        // cross (see BaselineProfileGenerator); find the field by focus.
        check(device.typeIntoFocusedField(QUERY)) { "the expanded search field could not be typed into" }
        device.pressEnter()
        device.waitForIdle()
    }

    private fun UiDevice.typeIntoFocusedField(text: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + NAV_WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val field: UiObject2? = findObject(By.clazz(EDIT_TEXT_CLASS).focused(true))
            if (field != null) {
                try {
                    field.text = text
                    return true
                } catch (_: StaleObjectException) {
                    waitForIdle()
                }
            }
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    private companion object {
        const val QUERY: String = "blue"
        const val EDIT_TEXT_CLASS: String = "android.widget.EditText"
        const val LIST_WAIT_MS: Long = 10_000
        const val NAV_WAIT_MS: Long = 5_000
        const val POLL_MS: Long = 100
        const val FLING_ROUNDS: Int = 3
        const val GESTURE_MARGIN_DIVISOR: Int = 5
    }
}
