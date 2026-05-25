package net.kikin.nubecita.core.push

import io.github.kikin81.atproto.app.bsky.notification.RegisterPushRequest
import io.github.kikin81.atproto.app.bsky.notification.UnregisterPushRequest
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.NoXrpcParams
import io.github.kikin81.atproto.runtime.UnitResponseSerializer
import net.kikin.nubecita.core.auth.XrpcClientProvider

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
class DefaultPushRegistrationRepository(
    private val xrpcClientProvider: XrpcClientProvider,
    private val appId: String,
) : PushRegistrationRepository {
    override suspend fun register(
        @Suppress("UNUSED_PARAMETER") did: String,
        fcmToken: String,
    ): Result<Unit> =
        runCatching {
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
        }

    override suspend fun unregister(
        @Suppress("UNUSED_PARAMETER") did: String,
        fcmToken: String,
    ): Result<Unit> =
        runCatching {
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
        }

    companion object {
        const val PROXY = "did:web:push.nubecita.app#bsky_notif"
        const val SERVICE_DID = "did:web:push.nubecita.app"
        const val PLATFORM_ANDROID = "android"
        const val NSID_REGISTER = "app.bsky.notification.registerPush"
        const val NSID_UNREGISTER = "app.bsky.notification.unregisterPush"
    }
}
