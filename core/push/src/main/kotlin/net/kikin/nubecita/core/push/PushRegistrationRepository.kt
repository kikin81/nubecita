package net.kikin.nubecita.core.push

/**
 * Registers / unregisters this device's FCM token with the gateway via the
 * user's PDS. The interface kept open is the seam
 * [PushRegistrationCoordinator] depends on; tests substitute in-memory fakes.
 *
 * Both methods return [Result] rather than throwing so the coordinator's
 * retry / backoff path can branch on success vs. failure without `try`/`catch`
 * at every call site. The implementation is [DefaultPushRegistrationRepository].
 *
 * The [did] parameter does NOT affect server-side behavior today — the gateway
 * derives the account from the DPoP-signed `Authorization` header the auth
 * layer attaches, not from the request body. It is retained on the API for
 * three reasons:
 *
 * - **Multi-account routing.** When the app grows past single-account, an
 *   `XrpcClientProvider` keyed on the active session DID lets the coordinator
 *   tell the repository "register for account X, not the currently-implicit
 *   one." Removing [did] now and re-adding it later would break every caller.
 * - **Logging.** `Timber.tag(...)` lines in the coordinator and any future
 *   diagnostic surface want the DID for correlation without forcing every
 *   caller to also pass it as a separate `args` map.
 * - **Local persistence.** The `(accountDid, fcmToken)` triple is what the
 *   [PushRegistrationStateStore] keys its no-op shortcut on, and the
 *   coordinator passes both to the repository so the wire-level and
 *   store-level keys stay aligned. Future call sites that bypass the
 *   coordinator (none today) still see this alignment in the API.
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
