package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * SDK-agnostic in-app-update controller — the only update type that escapes
 * `:core:update`. A singleton: its [state] and any in-flight download survive
 * Activity recreation. The [launcher] is Activity-scoped and passed per call
 * (never stored), so a recreated Activity simply hands in its fresh launcher.
 * Driven from the Activity (never a ViewModel — same boundary as `:core:review`).
 */
interface InAppUpdateController {
    val state: StateFlow<UpdateState>

    /** Check availability and, per [UpdatePolicy], launch FLEXIBLE/IMMEDIATE. Fail-silent. */
    suspend fun checkAndMaybePrompt(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** onResume catch-up: resume an interrupted IMMEDIATE; surface a backgrounded-DOWNLOADED FLEXIBLE. */
    suspend fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Install a DOWNLOADED flexible update (restarts the app). */
    suspend fun completeFlexibleUpdate()
}
