package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * The single Play-SDK boundary for in-app updates. Maps `AppUpdateInfo` →
 * [UpdateSignals] and the install listener → [InstallProgress]; everything else in
 * `:core:update` is SDK-free. Awaits Play's `Task`s via
 * `kotlinx-coroutines-play-services` (the 2.1.0 ktx accessor is a
 * `requestAppUpdateInfo` suspend function, not the `appUpdateInfo` property the
 * Task getter already exposes — so we stay on the Task + `.await()` form, matching
 * `PlayReviewClient`). The injected [manager] is the app-scoped
 * `AppUpdateManagerFactory.create(...)` (provided by the production module).
 */
internal class PlayAppUpdateClient
    @Inject
    constructor(
        private val manager: AppUpdateManager,
    ) : AppUpdateClient {
        private var listener: InstallStateUpdatedListener? = null

        override suspend fun fetchSignals(): UpdateSignals? =
            try {
                manager.appUpdateInfo.await().toUpdateSignals()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "fetchSignals failed: %s", e.javaClass.name)
                null
            }

        override fun startFlexible(launcher: ActivityResultLauncher<IntentSenderRequest>) = start(launcher, AppUpdateType.FLEXIBLE)

        override fun startImmediate(launcher: ActivityResultLauncher<IntentSenderRequest>) = start(launcher, AppUpdateType.IMMEDIATE)

        private fun start(
            launcher: ActivityResultLauncher<IntentSenderRequest>,
            type: Int,
        ) {
            try {
                manager.appUpdateInfo
                    .addOnSuccessListener { info ->
                        runCatching {
                            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
                        }.onFailure { Timber.w(it, "startUpdateFlow failed: %s", it.javaClass.name) }
                    }.addOnFailureListener { Timber.w(it, "appUpdateInfo fetch failed: %s", it.javaClass.name) }
            } catch (e: Exception) {
                Timber.w(e, "start(%d) failed: %s", type, e.javaClass.name)
            }
        }

        override suspend fun completeFlexibleUpdate() {
            try {
                manager.completeUpdate().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "completeUpdate failed: %s", e.javaClass.name)
            }
        }

        override fun registerListener(onProgress: (InstallProgress) -> Unit) {
            unregisterListener()
            val l =
                InstallStateUpdatedListener { state ->
                    onProgress(InstallProgress(state.installStatus(), state.bytesDownloaded(), state.totalBytesToDownload()))
                }
            listener = l
            manager.registerListener(l)
        }

        override fun unregisterListener() {
            listener?.let { manager.unregisterListener(it) }
            listener = null
        }

        private fun AppUpdateInfo.toUpdateSignals(): UpdateSignals =
            UpdateSignals(
                availability = updateAvailability(),
                installStatus = installStatus(),
                isFlexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                isImmediateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                updatePriority = updatePriority(),
                stalenessDays = clientVersionStalenessDays(),
                availableVersionCode = availableVersionCode(),
            )
    }
