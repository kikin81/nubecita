package net.kikin.nubecita.wear.screenshots

import android.content.ComponentName
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import net.kikin.nubecita.wear.BuildConfig
import net.kikin.nubecita.wear.MainActivity
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.widget.LauncherWidgetService
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Play Store screenshots for the Wear listing, captured by the
 * `wear_screenshots` fastlane lane on a Wear device or emulator.
 *
 * Runs against the bench flavor only (a signed-in fake session and a fixed
 * clock, no paired phone needed); the production variant skips it. Captures
 * are raw full-screen 1:1 images with no frame, as Play requires for Wear
 * (WO-G5). The last one is the launcher tile, which the system tile host draws
 * outside the app, so the journey adds it to the carousel with the Wear
 * debug-surface broadcast and captures the host's screen.
 */
@RunWith(AndroidJUnit4::class)
class WearMarketingScreenshotJourney {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        assumeTrue("bench flavor only", BuildConfig.FLAVOR == "bench")
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        device.wakeUp()
    }

    @Test
    fun feedAndPost() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The app opens on the landing screen; Home is the feed.
            val home = appString(R.string.wear_landing_home)
            assertTrue("landing screen never appeared", device.wait(Until.hasObject(By.desc(home)), TIMEOUT_MS))
            device.findObject(By.desc(home)).click()
            // The bench feed: fictional accounts, generated images, fixed clock.
            assertTrue("bench feed never appeared", device.wait(Until.hasObject(By.text("Ana Ruiz")), TIMEOUT_MS))
            // Scroll past the header so the first post sits whole in the middle.
            scrollBy(from = 6, to = 4)
            Screengrab.screenshot("01_feed")

            device.findObject(By.text("Ana Ruiz")).click()
            assertTrue("post screen never opened", device.wait(Until.hasObject(By.textContains("Worth the cold hands")), TIMEOUT_MS))
            // Bring the whole image into the middle of the screen.
            scrollBy(from = 7, to = 2)
            Screengrab.screenshot("02_post")
        }
    }

    @Test
    fun messagesConversationAndReply() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // "Messages", or "Messages · N" while something is unread.
            val messages = appString(R.string.wear_dms_open)
            assertTrue("messages button never appeared", device.wait(Until.hasObject(By.descStartsWith(messages)), TIMEOUT_MS))
            device.findObject(By.descStartsWith(messages)).click()
            assertTrue("conversation list never opened", device.wait(Until.hasObject(By.text("Weekend Trip")), TIMEOUT_MS))
            device.waitForIdle()
            Screengrab.screenshot("03_messages")

            device.findObject(By.text("Ana Ruiz")).click()
            assertTrue("conversation never opened", device.wait(Until.hasObject(By.text("Reply")), TIMEOUT_MS))
            device.waitForIdle()
            Screengrab.screenshot("04_conversation")

            device.findObject(By.text("Reply")).click()
            assertTrue("reply screen never opened", device.wait(Until.hasObject(By.text("Sounds good")), TIMEOUT_MS))
            device.waitForIdle()
            Screengrab.screenshot("05_reply")
        }
    }

    @Test
    fun launcherTile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tile = ComponentName(context, LauncherWidgetService::class.java).flattenToString()
        // Drop any copy a previous run left behind, so the tile is added fresh at a known index.
        debugSurface("remove-tile --ecn component $tile")
        try {
            // Inside the try, so the tile is removed again even if its index can't be read.
            val added = debugSurface("add-tile --ecn component $tile")
            val index =
                INDEX
                    .find(added)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt() ?: error("add-tile failed: $added")
            showTile(index)
            // The full-screen tile with the bench's unread count. A partial-height Wear OS 7
            // watch shows the short widget card instead, which has no tile container: use a
            // full-screen-tile emulator (e.g. Wear OS Large Round) for this capture.
            assertTrue("tile never showed", device.wait(Until.hasObject(By.res(SYSUI, "carousel_tile_container")), TIMEOUT_MS))
            val newMessages = context.resources.getQuantityString(R.plurals.wear_widget_new_messages, BENCH_UNREAD)
            assertTrue("tile never rendered the unread count", device.wait(Until.hasObject(By.text(newMessages)), TIMEOUT_MS))
            // Let the carousel finish settling the page in.
            device.waitForIdle()
            SystemClock.sleep(SETTLE_MS)
            Screengrab.screenshot("06_tile")
        } finally {
            // Leave the watch's tiles as they were. Best effort: a failure here must not
            // mask the assertion that failed above.
            debugSurface("remove-tile --ecn component $tile")
            goToWatchFace()
        }
    }

    /**
     * Brings tile [index] on screen: `show-tile` where the debug surface has it
     * (Wear OS 6 and older); on Wear OS 7, which only adds and removes tiles,
     * swipes there from the watch face.
     */
    private fun showTile(index: Int) {
        if (DEBUG_SURFACE_OK in debugSurface("show-tile --ei index $index")) return
        assertTrue("watch face never showed", goToWatchFace())
        repeat(index + 1) {
            device.swipe(device.displayWidth * 9 / 10, device.displayHeight / 2, device.displayWidth / 10, device.displayHeight / 2, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    /**
     * Home toggles between the watch face and the app launcher, so press it
     * until the watch face shows with no tile over it. False if it never does.
     */
    private fun goToWatchFace(): Boolean {
        repeat(3) {
            device.pressHome()
            device.waitForIdle()
            SystemClock.sleep(SETTLE_MS)
            if (device.hasObject(By.res(SYSUI, "watchface_overlay_container")) && !device.hasObject(By.res(SYSUI, "carousel_tile_container"))) return true
        }
        return false
    }

    /** Sends a Wear debug-surface operation as the shell user and returns `am`'s output. */
    private fun debugSurface(operation: String): String = device.executeShellCommand("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation $operation")

    /** A string in the app's current locale, so the journey matches whichever locale it captures. */
    private fun appString(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Swipes up the centre of the screen between two tenths of its height. */
    private fun scrollBy(
        from: Int,
        to: Int,
    ) {
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * from / 10, x, device.displayHeight * to / 10, SWIPE_STEPS)
        device.waitForIdle()
        SystemClock.sleep(SETTLE_MS)
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val SWIPE_STEPS = 40
        private const val SETTLE_MS = 1_000L
        private const val SYSUI = "com.google.android.clockwork.sysui"

        /** The bench repository's unread DM count, which the tile shows. */
        private const val BENCH_UNREAD = 2

        /** `am broadcast` reports a debug-surface success as result=1. */
        private const val DEBUG_SURFACE_OK = "result=1"

        /** `add-tile` answers with the new tile's carousel position. */
        private val INDEX = Regex("""Index=\[(\d+)]""")

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
