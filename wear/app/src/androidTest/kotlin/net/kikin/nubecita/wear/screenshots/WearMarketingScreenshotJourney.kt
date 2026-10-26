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
 * (WO-G5). W2 (feed) and W4 (messages) add their screens here.
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
    fun home() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(
                "signed-in home never appeared",
                device.wait(Until.hasObject(By.textContains("@nubecita.bsky.social")), TIMEOUT_MS),
            )
            Screengrab.screenshot("01_home")
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L

        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }
}
