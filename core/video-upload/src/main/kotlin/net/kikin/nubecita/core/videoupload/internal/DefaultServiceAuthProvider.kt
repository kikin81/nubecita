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

/**
 * Mints the service-auth JWT the Bluesky video service accepts.
 *
 * Two parameters here are counter-intuitive enough that getting either wrong
 * produces an opaque rejection, so both are asserted by tests:
 *
 * - **`aud` is the user's own PDS**, as `did:web:<pds-host>` — *not*
 *   `did:web:video.bsky.app`. The token authorises the video service to act
 *   against the user's repository, so the audience is that repository's host.
 * - **`lxm` is `com.atproto.repo.uploadBlob`** — *not*
 *   `app.bsky.video.uploadVideo`. The method being authorised is the blob
 *   write the video service ultimately performs on the user's behalf.
 *
 * Verified against docs.bsky.app's video tutorial and the reference
 * direct-upload implementation.
 */
internal class DefaultServiceAuthProvider(
    private val xrpcClientProvider: XrpcClientProvider,
    private val sessionStateProvider: SessionStateProvider,
    private val clock: Clock,
) : ServiceAuthProvider {
    private val mutex = Mutex()
    private var cached: CachedToken? = null

    override suspend fun videoServiceToken(): String =
        mutex.withLock {
            // One upload makes three authenticated calls — limits, upload,
            // poll. Re-minting for each would triple the PDS round-trips for
            // no benefit, so the token is reused within its lifetime.
            cached?.takeIf { it.isUsableAt(clock.now()) }?.let { return@withLock it.token }

            val session =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()

            val request =
                GetServiceAuthRequest(
                    aud = Did(audienceForPds(session.pdsUrl)),
                    lxm = Nsid(UPLOAD_BLOB_LXM),
                    exp = clock.now().plus(TOKEN_LIFETIME).epochSeconds,
                )

            ServerService(xrpcClientProvider.authenticated())
                .getServiceAuth(request)
                .token
                .also { cached = CachedToken(it, clock.now().plus(TOKEN_LIFETIME)) }
        }

    private data class CachedToken(
        val token: String,
        val expiresAt: kotlin.time.Instant,
    ) {
        /**
         * Treated as expired early. A token that passes the check and then
         * expires mid-upload fails a request that has already transferred
         * bytes, so the margin is worth more than the reuse it costs.
         */
        fun isUsableAt(now: kotlin.time.Instant): Boolean = now < expiresAt.minus(EXPIRY_MARGIN)
    }

    private companion object {
        /**
         * The blob-write method, not the video method. Counter-intuitive but
         * required — see the class KDoc.
         */
        const val UPLOAD_BLOB_LXM = "com.atproto.repo.uploadBlob"

        /** 30 minutes, per the reference implementation. */
        val TOKEN_LIFETIME = 1800.seconds

        val EXPIRY_MARGIN = 60.seconds
    }
}

/**
 * `did:web:<host>` for the account's PDS.
 *
 * The audience is the user's own PDS, not the video service. Extracted as a
 * pure function so the host-parsing edge cases are testable without a session:
 * `pdsUrl` is nullable on a freshly-restored session, and a value without a
 * parseable host cannot produce a valid audience.
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
