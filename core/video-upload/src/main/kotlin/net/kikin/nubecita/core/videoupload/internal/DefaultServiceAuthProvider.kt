package net.kikin.nubecita.core.videoupload.internal

import io.github.kikin81.atproto.com.atproto.server.GetServiceAuthRequest
import io.github.kikin81.atproto.com.atproto.server.ServerService
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Nsid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import java.net.URI
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The video service's own DID. */
internal const val VIDEO_SERVICE_DID = "did:web:video.bsky.app"

/** The blob-write method the video service performs on the user's behalf. */
internal const val UPLOAD_BLOB_LXM = "com.atproto.repo.uploadBlob"

/**
 * Mints the service-auth JWTs the Bluesky video flow needs.
 *
 * **There are two different audiences, and using one for both fails.** This
 * was established against the live service, not from documentation:
 *
 * - Calls the video service *answers itself* — `getUploadLimits`,
 *   `getJobStatus` — need a token addressed to **`did:web:video.bsky.app`**.
 *   Sending a PDS-addressed token returns
 *   `invalid_token: invalid token audience "did:web:<pds>", should be the
 *   video service host did:web:"video.bsky.app"` (HTTP 401).
 * - The **upload** call needs a token addressed to the user's **own PDS**
 *   with `lxm = com.atproto.repo.uploadBlob`, because what it authorises is
 *   the video service writing a blob into the user's repository. This is the
 *   shape docs.bsky.app documents, and it covers only that one leg.
 *
 * Tokens are cached per (audience, method) within their lifetime: a single
 * upload makes three authenticated calls, and re-minting for each would
 * triple the PDS round-trips for no benefit.
 */
internal class DefaultServiceAuthProvider(
    private val xrpcClientProvider: XrpcClientProvider,
    private val sessionStateProvider: SessionStateProvider,
    private val clock: Clock,
) : ServiceAuthProvider {
    private val mutex = Mutex()
    private val cache = mutableMapOf<TokenKey, CachedToken>()

    override suspend fun videoServiceToken(lxm: String): String = token(TokenKey(VIDEO_SERVICE_DID, lxm))

    override suspend fun blobUploadToken(): String = token(TokenKey(audienceForPds(pdsUrl()), UPLOAD_BLOB_LXM))

    private suspend fun token(key: TokenKey): String =
        mutex.withLock {
            cache[key]?.takeIf { it.isUsableAt(clock.now()) }?.let { return@withLock it.token }

            val expiresAt = clock.now().plus(TOKEN_LIFETIME)
            ServerService(xrpcClientProvider.authenticated())
                .getServiceAuth(
                    GetServiceAuthRequest(
                        aud = Did(key.audience),
                        lxm = Nsid(key.lxm),
                        exp = expiresAt.epochSeconds,
                    ),
                ).token
                .also { cache[key] = CachedToken(it, expiresAt) }
        }

    private fun pdsUrl(): String? =
        (sessionStateProvider.state.value as? SessionState.SignedIn)?.pdsUrl
            ?: throw NoSessionException()

    private data class TokenKey(
        val audience: String,
        val lxm: String,
    )

    private data class CachedToken(
        val token: String,
        val expiresAt: Instant,
    ) {
        /**
         * Treated as expired early. A token that passes the check and then
         * expires mid-upload fails a request that has already transferred
         * bytes, so the margin is worth more than the reuse it costs.
         */
        fun isUsableAt(now: Instant): Boolean = now < expiresAt.minus(EXPIRY_MARGIN)
    }

    private companion object {
        /** 30 minutes, per the reference implementation. */
        val TOKEN_LIFETIME = 1800.seconds

        val EXPIRY_MARGIN = 60.seconds
    }
}

/**
 * `did:web:<host>` for the account's PDS.
 *
 * Used **only** for the upload leg's token — see [DefaultServiceAuthProvider]
 * for why the video service's own calls take a different audience.
 *
 * Extracted as a pure function so the host-parsing edges are testable without a
 * session: `pdsUrl` is nullable on a freshly-restored session, and a value
 * without a parseable host cannot produce a valid audience.
 */
internal fun audienceForPds(pdsUrl: String?): String {
    val host =
        pdsUrl
            ?.let { runCatching { URI(it).host }.getOrNull() }
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: throw NoSessionException("Session has no usable PDS host for service auth")
    return "did:web:$host"
}
