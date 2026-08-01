package net.kikin.nubecita.core.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import java.io.IOException
import java.net.URI
import java.security.GeneralSecurityException
import javax.inject.Inject

internal class DefaultSessionStateProvider
    @Inject
    constructor(
        private val sessionReader: SessionReader,
        private val telemetry: SessionTelemetry,
        private val resultStream: SessionResultStream,
        @ApplicationScope private val scope: CoroutineScope,
    ) : SessionStateProvider {
        private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
        override val state: StateFlow<SessionState> = _state.asStateFlow()

        private val observerMutex = Mutex()
        private var observerJob: Job? = null

        /**
         * Bumped every time the observer publishes. [refresh] samples it before its
         * read and skips its own write if the observer published in the meantime —
         * the observer's value is then at least as fresh, and a pull-path write
         * would be a stale clobber the store has no reason to correct.
         */
        @Volatile
        private var observerGeneration = 0L

        init {
            scope.launch { startObserving() }
        }

        /**
         * Observes the store continuously so a write nobody in this app initiated
         * still moves the state. The motivating case is the SDK's
         * `DpopAuthProvider.failRefresh` clearing the session on `invalid_grant`:
         * with a pull-only provider the app kept serving a dead session until
         * something happened to call [refresh] (next cold start, or a worker), so
         * the user went on tapping like/follow against a revoked token and only
         * landed on Login much later (nubecita-kzsd).
         *
         * [_state] stays a `MutableStateFlow` rather than becoming a derived
         * `stateIn()`: SplashScreen's `setKeepOnScreenCondition` reads
         * `state.value` synchronously from the platform's frame callback.
         *
         * A terminal read error completes this collector rather than looping. An
         * unbounded retry would spin forever against a permanently broken store
         * (an invalidated Keystore never recovers), and burning the battery to
         * re-observe a store we have no evidence is readable is the wrong trade.
         * [refresh] restarts it instead — but only once a read has actually
         * succeeded, so a still-broken store can't re-arm a doomed collector.
         */
        private suspend fun startObserving() {
            observerMutex.withLock {
                if (observerJob?.isActive == true) return
                observerJob =
                    scope.launch {
                        resultStream
                            .results()
                            .retryWhen { cause, attempt ->
                                val retryable =
                                    cause.isTransientStorageFailure() && attempt < RETRY_DELAYS_MILLIS.size
                                if (retryable) {
                                    telemetry.onSessionReadError(cause)
                                    delay(RETRY_DELAYS_MILLIS[attempt.toInt()])
                                }
                                retryable
                            }.catch { cause ->
                                if (!cause.isTransientStorageFailure()) throw cause
                                // Same contract as refresh(): every bounded retry
                                // failed, so route to Login rather than dead-ending
                                // on the splash. The session file is untouched.
                                telemetry.onSessionReadErrorTerminal(cause)
                                emit(SessionLoadResult.Absent)
                            }.collect { result ->
                                // No ReadError arrives here: the stream throws on a
                                // storage failure rather than emitting one, and the
                                // catch above converts a terminal failure to Absent.
                                _state.value = result.toSessionState()
                                observerGeneration++
                            }
                    }
            }
        }

        override suspend fun refresh() {
            val generationAtReadStart = observerGeneration
            val result = loadWithBoundedRetry()
            if (result is SessionLoadResult.ReadError) {
                // Every bounded retry failed. Route to Login rather than
                // dead-ending the splash — the session file is untouched, so
                // a later cold start can still recover — but record the
                // terminal event first: this is the user-visible spurious
                // logout the retries exist to prevent (epic nubecita-09xt).
                telemetry.onSessionReadErrorTerminal(result.cause)
            }

            // Publish BEFORE (re)starting the observer. startObserving() launches
            // the collector on another coroutine; if it published first, the
            // assignment below would overwrite its fresher value with the one read
            // above, and DataStore only re-emits on the next write — so nothing
            // would correct it.
            //
            // The generation check closes the same hole against an already-running
            // collector: if it published anything while loadWithBoundedRetry() was
            // suspended, its value is at least as fresh as ours, so leave it alone.
            // Without this, an SDK clear landing mid-read would be overwritten with
            // the SignedIn we read just before it — reinstating exactly the zombie
            // session this class exists to prevent.
            if (observerGeneration == generationAtReadStart) {
                _state.value = result.toSessionState()
            }

            if (result !is SessionLoadResult.ReadError) {
                // The store just read cleanly, so a collector killed by an earlier
                // terminal failure can be revived without risking a hot loop.
                startObserving()
            }
        }

        private fun SessionLoadResult.toSessionState(): SessionState =
            when (this) {
                is SessionLoadResult.Loaded -> {
                    val handle = session.handle
                    val did = session.did
                    when {
                        // atproto-kotlin v8 made OAuthSession.{did,handle,pdsUrl} nullable: a freshly-
                        // minted signup session may transiently have null identity until the new
                        // account's DID document is resolvable via the PLC directory. Treat that
                        // window as Loading so MainActivity's splash overlay stays up; the next
                        // emission (after completeLogin's bounded-retry hydration writes the resolved
                        // session) lands on SignedIn with non-null fields.
                        handle != null && did != null ->
                            SessionState.SignedIn(handle = handle, did = did, pdsUrl = session.pdsUrl)
                        else -> SessionState.Loading
                    }
                }
                SessionLoadResult.Absent -> SessionState.SignedOut
                is SessionLoadResult.ReadError -> SessionState.SignedOut
            }

        private fun Throwable.isTransientStorageFailure(): Boolean = this is IOException || this is GeneralSecurityException || this is SerializationException

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
