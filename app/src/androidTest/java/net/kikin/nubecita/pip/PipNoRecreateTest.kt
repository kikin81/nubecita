package net.kikin.nubecita.pip

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.MainActivity
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * Guards the load-bearing manifest change in q5ge.4 (design D7): `MainActivity`
 * declares `configChanges` covering the PiP transition, so entering
 * Picture-in-Picture does NOT recreate the Activity (which would tear down the
 * Nav3 back stack mid-transition). Without those flags this test fails — the
 * Activity instance after the transition would differ from the one before.
 *
 * Enters PiP directly through the platform API (not the app's gated bridge) to
 * isolate the manifest behavior from the Pro entitlement check.
 *
 * `run-instrumented`-gated: needs a PiP-capable device/emulator; skips cleanly
 * via `assumeTrue` where the feature is absent.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PipNoRecreateTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        // Initialize the Hilt test component before launching the
        // @AndroidEntryPoint MainActivity, matching the repo's Hilt
        // instrumentation-test convention (e.g. FeedScreenInstrumentationTest).
        hiltRule.inject()
    }

    @Test
    fun enteringPip_doesNotRecreateActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Device does not support Picture-in-Picture",
            instrumentation.targetContext.packageManager
                .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            val before = AtomicReference<MainActivity>()
            scenario.onActivity { before.set(it) }

            // Programmatic entry from the resumed state — bypasses the Pro gate
            // and onUserLeaveHint, exercising only the configChanges path.
            scenario.onActivity { activity ->
                activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                assertSame(
                    "MainActivity was recreated on the PiP transition — configChanges is missing or incomplete",
                    before.get(),
                    activity,
                )
                assertTrue("Activity did not enter PiP mode", activity.isInPictureInPictureMode)
            }
        }
    }
}
