package net.kikin.nubecita.core.push

/**
 * Registers / unregisters this device's FCM token with the gateway via the
 * user's PDS. The interface kept open is the seam
 * [PushRegistrationCoordinator] depends on; tests substitute in-memory fakes.
 *
 * Both methods return [Result] rather than throwing so the coordinator's
 * retry / backoff path can branch on success vs. failure without `try`/`catch`
 * at every call site. The implementation is [DefaultPushRegistrationRepository].
 */
interface PushRegistrationRepository {
    suspend fun register(
        did: String,
        fcmToken: String,
    ): Result<Unit>

    suspend fun unregister(
        did: String,
        fcmToken: String,
    ): Result<Unit>
}
