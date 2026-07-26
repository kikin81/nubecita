package net.kikin.nubecita.core.videoupload.internal

import io.github.kikin81.atproto.runtime.AuthProvider
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient

/** Host that serves `app.bsky.video.*`. Not the user's PDS. */
internal const val VIDEO_SERVICE_BASE_URL = "https://video.bsky.app"

/**
 * Mints the short-lived JWT the video service accepts.
 *
 * Implemented in the service-auth slice; declared here because the limits
 * probe is the first caller and should depend on the capability rather than on
 * how it is obtained.
 */
internal interface ServiceAuthProvider {
    /**
     * Token for a call the video service answers itself, bound to [lxm].
     *
     * Addressed to the video service, not the user's PDS — a PDS-addressed
     * token is rejected with `invalid_token: invalid token audience`.
     */
    suspend fun videoServiceToken(lxm: String): String

    /**
     * Token authorising the video service to write a blob into the user's own
     * repository. Addressed to the user's PDS with `com.atproto.repo.uploadBlob`.
     */
    suspend fun blobUploadToken(): String
}

/**
 * Attaches `Authorization: Bearer <serviceAuthToken>`, resolving the token per
 * request rather than at construction.
 *
 * The SDK ships `BearerTokenAuth`, but it captures a fixed string. A
 * service-auth token expires in 30 minutes and is minted lazily, so binding it
 * at construction would pin a token that may not exist yet.
 */
internal class ServiceAuthTokenAuth(
    private val token: suspend () -> String,
) : AuthProvider {
    override suspend fun authHeaders(
        method: String,
        url: String,
    ): Map<String, String> = mapOf("Authorization" to "Bearer ${token()}")
}

/**
 * Builds the `XrpcClient` for `app.bsky.video.getUploadLimits` and
 * `getJobStatus`.
 *
 * Both are ordinary typed queries, so they go through the SDK rather than raw
 * Ktor — unlike the upload leg, which needs byte-level progress the SDK does
 * not expose.
 *
 * The singleton [HttpClient] is reused deliberately. It carries timeouts,
 * logging and the connection pool but **no** credentials: in this SDK auth is
 * an `AuthProvider` on the `XrpcClient`, never a plugin on the transport. So
 * sharing it cannot leak the PDS session's DPoP-bound token to this host, and
 * a second engine would just duplicate the pool.
 */
internal class VideoServiceClientFactory(
    private val httpClient: HttpClient,
    private val serviceAuthProvider: ServiceAuthProvider,
) {
    /**
     * @param lxm the method this client will call. The token is bound to it,
     *   so a client built for one method cannot be reused for another.
     */
    fun create(lxm: String): XrpcClient =
        XrpcClient(
            baseUrl = VIDEO_SERVICE_BASE_URL,
            httpClient = httpClient,
            authProvider = ServiceAuthTokenAuth { serviceAuthProvider.videoServiceToken(lxm) },
        )
}
