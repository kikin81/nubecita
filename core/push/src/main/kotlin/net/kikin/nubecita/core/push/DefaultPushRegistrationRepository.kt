package net.kikin.nubecita.core.push

import io.github.kikin81.atproto.app.bsky.notification.NotificationService
import io.github.kikin81.atproto.app.bsky.notification.RegisterPushRequest
import io.github.kikin81.atproto.app.bsky.notification.UnregisterPushRequest
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.XrpcClientProvider
import timber.log.Timber

/**
 * Calls the user's PDS to register / unregister this device's FCM token
 * against the self-hosted gateway at `https://push.nubecita.app`.
 *
 * Uses the generated [NotificationService] wrapper from atproto-kotlin
 * (≥ 9.0.0, which added the per-method `proxy: String?` parameter via
 * `kikin81/atproto-kotlin#117`). The `atproto-proxy: did:web:push.nubecita.app#bsky_notif`
 * header is what tells the PDS to forward the call to our gateway
 * instead of bsky's own notification service.
 *
 * Both methods return [Result] rather than throwing so the
 * [PushRegistrationCoordinator]'s retry / backoff path can branch on
 * success vs. failure without `try`/`catch` plumbing at every call site.
 */
internal class DefaultPushRegistrationRepository(
    private val xrpcClientProvider: XrpcClientProvider,
    private val appId: String,
    private val gateway: PushGatewayConfig,
) : PushRegistrationRepository {
    override suspend fun register(
        @Suppress("UNUSED_PARAMETER") did: String,
        fcmToken: String,
    ): Result<Unit> =
        runCatchingExceptCancellation {
            NotificationService(xrpcClientProvider.authenticated()).registerPush(
                request =
                    RegisterPushRequest(
                        serviceDid = Did(gateway.serviceDid),
                        token = fcmToken,
                        platform = PLATFORM_ANDROID,
                        appId = appId,
                    ),
                proxy = gateway.proxyDid,
            )
        }.onFailure(::logRegistrationFailure)

    override suspend fun unregister(
        @Suppress("UNUSED_PARAMETER") did: String,
        fcmToken: String,
    ): Result<Unit> =
        runCatchingExceptCancellation {
            NotificationService(xrpcClientProvider.authenticated()).unregisterPush(
                request =
                    UnregisterPushRequest(
                        serviceDid = Did(gateway.serviceDid),
                        token = fcmToken,
                        platform = PLATFORM_ANDROID,
                        appId = appId,
                    ),
                proxy = gateway.proxyDid,
            )
        }.onFailure(::logRegistrationFailure)

    // Diagnostic-only logging on every (un)register failure. The
    // PushRegistrationCoordinator already retries with exponential backoff
    // and writes Pending/Failed to the store, but neither path surfaces
    // WHY the call failed. atproto-kotlin's XrpcError carries the parsed
    // {error, message} JSON body plus the HTTP status, which is exactly
    // what's needed to disambiguate scope / auth / gateway / PDS-side
    // rejections without a packet capture.
    private fun logRegistrationFailure(failure: Throwable) {
        if (failure is XrpcError) {
            // Pass the throwable so the stack trace lands in logcat alongside
            // the parsed XrpcError fields. The parsed fields disambiguate
            // scope / auth / proxy errors at a glance; the stack tells you
            // which call site and serializer touched the response.
            Timber
                .tag(TAG)
                .e(
                    failure,
                    "(un)register failed: status=%d errorName=%s message=%s",
                    failure.status,
                    failure.errorName,
                    failure.errorMessage,
                )
        } else {
            Timber.tag(TAG).e(failure, "(un)register failed with non-XrpcError")
        }
    }

    companion object {
        // `PLATFORM_ANDROID` is a protocol-level constant required by the
        // `app.bsky.notification.registerPush` lexicon, not a gateway-level
        // value — so it stays here rather than moving to [PushGatewayConfig].
        // Gateway identity (serviceDid + proxyDid) lives in
        // [PushGatewayConfig.Nubecita] and is injected via Hilt.
        const val PLATFORM_ANDROID = "android"
        private const val TAG = "PushRegistration"
    }
}
