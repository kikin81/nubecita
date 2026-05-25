package net.kikin.nubecita.core.push

import android.os.Build

/**
 * Decides whether the POST_NOTIFICATIONS runtime prompt should be shown on
 * the next eligible UI surface (today: the login success branch), and
 * records that decision once made.
 *
 * Two-axis gate:
 *  - SDK version: pre-API-33 (Android 12L and below) auto-grants
 *    POST_NOTIFICATIONS at install time, so triggering the runtime prompt
 *    is impossible. We must NOT flip the persisted "shown" flag in this
 *    case — if the user later upgrades to Android 13+, they should still
 *    get prompted on first post-upgrade login.
 *  - Persisted gate: once we've shown the prompt for this install, never
 *    show it again, regardless of whether the user granted or denied.
 *    Re-prompting on the same install would feel like an OS misfire.
 *
 * Constructed at Hilt-provider time with the host's runtime
 * [Build.VERSION.SDK_INT]; unit tests construct it directly with an explicit
 * `sdkInt` so the gate's pre-API-33 branch is exercisable on the JVM.
 */
class NotificationsPromptDecider(
    private val store: NotificationsPromptShownStore,
    private val sdkInt: Int,
) {
    suspend fun shouldPrompt(): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !store.read()

    suspend fun markPrompted() {
        store.markShown()
    }
}
