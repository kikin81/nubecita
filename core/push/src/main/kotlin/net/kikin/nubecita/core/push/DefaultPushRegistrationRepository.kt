package net.kikin.nubecita.core.push

import io.github.kikin81.atproto.app.bsky.notification.RegisterPushRequest
import io.github.kikin81.atproto.app.bsky.notification.UnregisterPushRequest
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.NoXrpcParams
import io.github.kikin81.atproto.runtime.UnitResponseSerializer
import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.XrpcClientProvider
import timber.log.Timber

/**
 * Calls the user's PDS to register / unregister this device's FCM token
 * against the self-hosted gateway at `https://push.nubecita.app`.
 *
 * Both methods bypass the generated `NotificationService.registerPush` /
 * `unregisterPush` wrappers in atproto-kotlin 8.1.0 and call
 * [io.github.kikin81.atproto.runtime.XrpcClient.procedure] directly, because
 * the generated wrappers do NOT yet expose a `proxy: String?` parameter and
 * the gateway contract requires the `atproto-proxy:
 * did:web:push.nubecita.app#bsky_notif` header. See the change's `design.md`
 * "`XrpcClient.procedure(...)` directly" decision; once the upstream
 * generator is patched (tracked in tasks §11.2) this class swaps back to the
 * generated path.
 *
 * Both methods return [Result] rather than throwing so the
 * [PushRegistrationCoordinator]'s retry / backoff path can branch on
 * success vs. failure without `try`/`catch` plumbing at every call site.
 */
internal class DefaultPushRegistrationRepository(
    private val xrpcClientProvider: XrpcClientProvider,
    private val appId: String,
) : PushRegistrationRepository {
    override suspend fun register(
        @Suppress("UNUSED_PARAMETER") did: String,
        fcmToken: String,
    ): Result<Unit> =
        runCatchingExceptCancellation {
            val client = xrpcClientProvider.authenticated()
            client.procedure(
                nsid = NSID_REGISTER,
                params = NoXrpcParams,
                paramsSerializer = NoXrpcParams.serializer(),
                input =
                    RegisterPushRequest(
                        serviceDid = Did(SERVICE_DID),
                        token = fcmToken,
                        platform = PLATFORM_ANDROID,
                        appId = appId,
                    ),
                inputSerializer = RegisterPushRequest.serializer(),
                responseSerializer = UnitResponseSerializer,
                proxy = PROXY,
            )
        }.onFailure(::logRegistrationFailure)

    override suspend fun unregister(
        @Suppress("UNUSED_PARAMETER") did: String,
        fcmToken: String,
    ): Result<Unit> =
        runCatchingExceptCancellation {
            val client = xrpcClientProvider.authenticated()
            client.procedure(
                nsid = NSID_UNREGISTER,
                params = NoXrpcParams,
                paramsSerializer = NoXrpcParams.serializer(),
                input =
                    UnregisterPushRequest(
                        serviceDid = Did(SERVICE_DID),
                        token = fcmToken,
                        platform = PLATFORM_ANDROID,
                        appId = appId,
                    ),
                inputSerializer = UnregisterPushRequest.serializer(),
                responseSerializer = UnitResponseSerializer,
                proxy = PROXY,
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
            Timber
                .tag(TAG)
                .e(
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
        const val PROXY = "did:web:push.nubecita.app#bsky_notif"
        const val SERVICE_DID = "did:web:push.nubecita.app"
        const val PLATFORM_ANDROID = "android"
        const val NSID_REGISTER = "app.bsky.notification.registerPush"
        const val NSID_UNREGISTER = "app.bsky.notification.unregisterPush"
        private const val TAG = "PushRegistration"
    }
}
