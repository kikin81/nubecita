package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * Internal seam over the Play app-update API. The only SDK importer is
 * [PlayAppUpdateClient]; faked in DefaultInAppUpdateController tests. The
 * [launcher] is the Activity's `StartIntentSenderForResult` launcher (an AndroidX
 * type, not a Play type — the Play SDK never leaks past this seam).
 */
internal interface AppUpdateClient {
    /** Fetches a fresh availability snapshot; null if it can't be obtained (offline / not from Play). */
    suspend fun fetchSignals(): UpdateSignals?

    fun startFlexible(launcher: ActivityResultLauncher<IntentSenderRequest>)

    fun startImmediate(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Completes a DOWNLOADED flexible update (restarts to install). */
    suspend fun completeFlexibleUpdate()

    fun registerListener(onProgress: (InstallProgress) -> Unit)

    fun unregisterListener()
}
