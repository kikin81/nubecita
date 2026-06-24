// Requires the run-instrumented PR label to execute in CI (connected-device job).
package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the production controller's two behaviours that the JVM policy/prefs tests
 * cannot exercise: the real-time install listener (DOWNLOADED → ReadyToInstall
 * the moment a foreground download finishes, no onResume) and the fire-time
 * throttle write — both against Play's real [FakeAppUpdateManager] wired through a
 * real [PlayAppUpdateClient].
 */
@RunWith(AndroidJUnit4::class)
class DefaultInAppUpdateControllerTest {
    private lateinit var fake: FakeAppUpdateManager
    private lateinit var prefs: FakeUpdatePreferences
    private lateinit var launcher: RecordingLauncher
    private lateinit var controller: DefaultInAppUpdateController

    private val availableVersionCode = 4242

    @Before
    fun setUp() {
        fake = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())
        prefs = FakeUpdatePreferences()
        launcher = RecordingLauncher()
        controller = DefaultInAppUpdateController(PlayAppUpdateClient(fake), prefs)
    }

    @Test
    fun foregroundFlexible_reachesReadyToInstall_withoutOnResume() =
        runTest {
            // A plain FLEXIBLE-eligible update (priority 0, fresh) → policy picks Flexible.
            fake.setUpdateAvailable(availableVersionCode)

            controller.checkAndMaybePrompt(launcher)
            assertTrue("expected a FLEXIBLE flow to start", fake.isConfirmationDialogVisible)

            // Drive the real Play flow; the controller's listener (armed in
            // checkAndMaybePrompt) must surface each state in real time.
            fake.userAcceptsUpdate()
            fake.downloadStarts()
            assertTrue(controller.state.value is UpdateState.Downloading)

            fake.downloadCompletes()
            assertEquals(UpdateState.ReadyToInstall, controller.state.value)
        }

    @Test
    fun throttle_isWrittenAtFireTime_andSuppressesSecondPromptForSameVersion() =
        runTest {
            fake.setUpdateAvailable(availableVersionCode)
            assertNull(prefs.lastPromptedVersionCode())

            controller.checkAndMaybePrompt(launcher)
            assertEquals(availableVersionCode, prefs.lastPromptedVersionCode())
            assertTrue("first call should start a flow", fake.isConfirmationDialogVisible)

            // A fresh fake at the SAME versionCode: the throttle must suppress a
            // second flow because lastPromptedVersionCode already matches.
            val secondFake = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())
            secondFake.setUpdateAvailable(availableVersionCode)
            val secondController = DefaultInAppUpdateController(PlayAppUpdateClient(secondFake), prefs)

            secondController.checkAndMaybePrompt(launcher)
            assertTrue("second call must not start a flow", !secondFake.isConfirmationDialogVisible)
        }

    /** In-memory [UpdatePreferences] — JVM prefs are unit-tested elsewhere. */
    private class FakeUpdatePreferences : UpdatePreferences {
        private var stored: Int? = null

        override suspend fun lastPromptedVersionCode(): Int? = stored

        override suspend fun setLastPromptedVersionCode(versionCode: Int) {
            stored = versionCode
        }
    }

    /**
     * Minimal [ActivityResultLauncher] double. The FakeAppUpdateManager's
     * userAcceptsUpdate()-driven flow never dispatches the IntentSender, so the
     * launch path is never invoked; this records launches only to stay a real,
     * non-null launcher the SDK can accept.
     */
    private class RecordingLauncher : ActivityResultLauncher<IntentSenderRequest>() {
        var launchCount = 0
            private set

        override fun launch(
            input: IntentSenderRequest,
            options: androidx.core.app.ActivityOptionsCompat?,
        ) {
            launchCount++
        }

        override fun unregister() = Unit

        override val contract: ActivityResultContract<IntentSenderRequest, *> =
            object : ActivityResultContract<IntentSenderRequest, Unit>() {
                override fun createIntent(
                    context: android.content.Context,
                    input: IntentSenderRequest,
                ) = android.content.Intent()

                override fun parseResult(
                    resultCode: Int,
                    intent: android.content.Intent?,
                ) = Unit
            }
    }
}
