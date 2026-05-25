package net.kikin.nubecita.core.push

/**
 * Injection seam over `FirebaseMessaging.getInstance().token` so the
 * coordinator can run under a TestScope without depending on a live
 * `FirebaseApp`. The Android-bound implementation wraps the Play-services
 * `Task` via `kotlinx.coroutines.tasks.await()`.
 */
interface FcmTokenProvider {
    /**
     * Returns the device's current FCM registration token. Throws on the
     * rare cases the Play Services SDK surfaces a failure (no Play Services
     * on the device, IID server unreachable, etc.) — the coordinator's
     * `runCatching` boundary maps those to `Result.failure` and the backoff
     * loop retries from there.
     */
    suspend fun current(): String
}
