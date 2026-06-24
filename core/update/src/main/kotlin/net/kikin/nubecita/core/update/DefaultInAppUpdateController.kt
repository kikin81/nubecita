package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [InAppUpdateController]. Runs the pure [UpdatePolicy] over signals
 * from [AppUpdateClient], throttling FLEXIBLE to once per availableVersionCode
 * (written at fire time via [UpdatePreferences], not on the droppable
 * ActivityResult). Owns the install listener → real-time [UpdateState]
 * (DOWNLOADED → ReadyToInstall the moment a foreground download finishes);
 * unregisters on a terminal install state. Errors are swallowed to [UpdateState]
 * and logged with Timber.w (CancellationException always rethrown). No onDestroy —
 * the singleton survives config changes.
 */
@Singleton
internal class DefaultInAppUpdateController
    @Inject
    constructor(
        private val client: AppUpdateClient,
        private val preferences: UpdatePreferences,
    ) : InAppUpdateController {
        private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        override val state: StateFlow<UpdateState> = _state.asStateFlow()

        override suspend fun checkAndMaybePrompt(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            try {
                val signals = client.fetchSignals() ?: return
                when (UpdatePolicy.decide(signals, preferences.lastPromptedVersionCode())) {
                    UpdateAction.Immediate -> client.startImmediate(launcher)
                    UpdateAction.Flexible -> {
                        ensureListener()
                        // Fire-time throttle write: stamp before launching so a dropped
                        // ActivityResult can never un-throttle the prompt.
                        preferences.setLastPromptedVersionCode(signals.availableVersionCode)
                        client.startFlexible(launcher)
                    }
                    UpdateAction.None -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "checkAndMaybePrompt failed: %s", e.javaClass.name)
            }
        }

        override suspend fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            // Catch-up only — never registers a listener for users with no in-flight update.
            // Fetch fresh signals, then: resume an interrupted IMMEDIATE update; for an
            // in-progress FLEXIBLE download re-arm the listener (it does not fire for a
            // download that completed before it was re-registered) and surface a
            // download that already finished while the app was backgrounded.
            try {
                val signals = client.fetchSignals() ?: return
                if (signals.availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    if (signals.isImmediateAllowed) client.startImmediate(launcher)
                    return
                }
                when (signals.installStatus) {
                    InstallStatusModel.PENDING, InstallStatusModel.DOWNLOADING -> ensureListener()
                    InstallStatusModel.DOWNLOADED -> {
                        ensureListener()
                        _state.value = UpdateState.ReadyToInstall
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "onResume catch-up failed: %s", e.javaClass.name)
            }
        }

        override suspend fun completeFlexibleUpdate() {
            try {
                client.completeFlexibleUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "completeFlexibleUpdate failed: %s", e.javaClass.name)
            }
        }

        // `listening` is only ever touched on the main thread — both ensureListener()
        // callers (checkAndMaybePrompt/onResume from the Activity) and Play's
        // InstallStateUpdatedListener callbacks (delivered on the main looper) — so
        // no @Volatile/synchronization is needed.
        private var listening = false

        private fun ensureListener() {
            if (listening) return
            listening = true
            client.registerListener { progress ->
                _state.value =
                    when (progress.status) {
                        InstallStatusModel.DOWNLOADING ->
                            UpdateState.Downloading(progress.bytesDownloaded, progress.totalBytesToDownload)
                        InstallStatusModel.DOWNLOADED -> UpdateState.ReadyToInstall
                        InstallStatusModel.FAILED -> {
                            client.unregisterListener()
                            listening = false
                            UpdateState.Failed
                        }
                        InstallStatusModel.INSTALLED, InstallStatusModel.CANCELED -> {
                            client.unregisterListener()
                            listening = false
                            UpdateState.Idle
                        }
                        else -> _state.value
                    }
            }
        }
    }
