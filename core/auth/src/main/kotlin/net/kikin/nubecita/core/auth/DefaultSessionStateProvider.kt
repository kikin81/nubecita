package net.kikin.nubecita.core.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import javax.inject.Inject

internal class DefaultSessionStateProvider
    @Inject
    constructor(
        private val sessionReader: SessionReader,
        private val telemetry: SessionTelemetry,
    ) : SessionStateProvider {
        private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
        override val state: StateFlow<SessionState> = _state.asStateFlow()

        override suspend fun refresh() {
            when (val result = loadWithBoundedRetry()) {
                is SessionLoadResult.Loaded -> {
                    val handle = result.session.handle
                    val did = result.session.did
                    _state.value =
                        when {
                            // atproto-kotlin v8 made OAuthSession.{did,handle,pdsUrl} nullable: a freshly-
                            // minted signup session may transiently have null identity until the new
                            // account's DID document is resolvable via the PLC directory. Treat that
                            // window as Loading so MainActivity's splash overlay stays up; the next
                            // refresh() (triggered after completeLogin's bounded-retry hydration) lands
                            // on SignedIn with non-null fields.
                            handle != null && did != null ->
                                SessionState.SignedIn(handle = handle, did = did, pdsUrl = result.session.pdsUrl)
                            else -> SessionState.Loading
                        }
                }
                SessionLoadResult.Absent -> _state.value = SessionState.SignedOut
                is SessionLoadResult.ReadError -> {
                    // Every bounded retry failed. Route to Login rather than
                    // dead-ending the splash — the session file is untouched, so
                    // a later cold start can still recover — but record the
                    // terminal event first: this is the user-visible spurious
                    // logout the retries exist to prevent (epic nubecita-09xt).
                    telemetry.onSessionReadErrorTerminal(result.cause)
                    _state.value = SessionState.SignedOut
                }
            }
        }

        /**
         * A [SessionLoadResult.ReadError] is usually transient (Keystore not yet
         * available just after boot, disk contention), so it is retried with a
         * short bounded backoff while the caller stays in [SessionState.Loading]
         * (the splash). [SessionLoadResult.Absent] and
         * [SessionLoadResult.Loaded] return immediately — a genuinely
         * signed-out user never waits on the retry schedule.
         */
        private suspend fun loadWithBoundedRetry(): SessionLoadResult {
            var result = sessionReader.loadResult()
            for (delayMillis in RETRY_DELAYS_MILLIS) {
                if (result !is SessionLoadResult.ReadError) return result
                delay(delayMillis)
                result = sessionReader.loadResult()
            }
            return result
        }

        private companion object {
            /** ~5s total: covers post-boot Keystore latency without holding the splash hostage. */
            val RETRY_DELAYS_MILLIS = longArrayOf(500, 1_500, 3_000)
        }
    }

/**
 * Classify a PDS service endpoint as self-hosted (a non-Bluesky-operated PDS).
 *
 * Bluesky operates BOTH the entryway host `bsky.social` AND the per-user
 * `*.host.bsky.network` PDS hosts (e.g. `hollowfoot.us-west.host.bsky.network`),
 * so a naive `!host.contains("bsky.social")` check would misclassify every
 * bsky.network-hosted account as self-hosted. Self-hosted = the host is neither
 * `bsky.social` nor `host.bsky.network` nor a subdomain of `host.bsky.network`.
 * An absent/unparseable host can't be classified → treated as not self-hosted.
 */
internal fun isSelfHostedPds(pdsUrl: String?): Boolean {
    val host = pdsUrl?.let { runCatching { URI(it).host }.getOrNull() }?.lowercase()
    if (host.isNullOrEmpty()) return false
    val bskyOperated =
        host == "bsky.social" || host == "host.bsky.network" || host.endsWith(".host.bsky.network")
    return !bskyOperated
}
