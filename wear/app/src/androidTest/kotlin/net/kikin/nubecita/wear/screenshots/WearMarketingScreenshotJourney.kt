package net.kikin.nubecita.wear.screenshots

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
 * (WO-G5). W4 (messages) adds its screens here.
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
    fun feedPostAndFeeds() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The bench feed: fictional accounts, generated images, fixed clock.
            assertTrue("bench feed never appeared", device.wait(Until.hasObject(By.text("Ana Ruiz")), TIMEOUT_MS))
            device.waitForIdle()
            Screengrab.screenshot("01_feed")

            // Scroll so the first post's photo is on screen.
            device.swipe(device.displayWidth / 2, device.displayHeight * 9 / 10, device.displayWidth / 2, device.displayHeight / 6, SWIPE_STEPS)
            device.waitForIdle()
            Screengrab.screenshot("02_feed_photo")

            device.findObject(By.text("Ana Ruiz"))?.click()
            assertTrue("post screen never opened", device.wait(Until.hasObject(By.textContains("Worth the cold hands")), TIMEOUT_MS))
            device.waitForIdle()
            Screengrab.screenshot("03_post")
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val SWIPE_STEPS = 40

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
