package net.kikin.nubecita.wear.screenshots

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import net.kikin.nubecita.wear.BuildConfig
import net.kikin.nubecita.wear.MainActivity
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
 * (WO-G5).
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
            assertTrue("messages button never appeared", device.wait(Until.hasObject(By.descStartsWith("Messages")), TIMEOUT_MS))
            device.findObject(By.descStartsWith("Messages")).click()
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

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
