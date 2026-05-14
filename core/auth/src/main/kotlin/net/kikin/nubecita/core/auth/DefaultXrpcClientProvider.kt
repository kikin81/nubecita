package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.core.auth.di.AuthCoroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [XrpcClientProvider] backed by [AtOAuth.createClient]. Caches
 * the constructed [XrpcClient] keyed by the active session's DID; the
 * cache is mutex-guarded so concurrent callers from a cold cache trigger
 * exactly one `createClient()` invocation, and a session-state collector
 * eagerly invalidates the cache when the active DID changes (sign-out,
 * refresh failure, sign-in as a different account).
 */
@Singleton
internal class DefaultXrpcClientProvider
    @Inject
    constructor(
        private val atOAuth: AtOAuth,
        private val sessionStateProvider: SessionStateProvider,
        @AuthCoroutineScope coroutineScope: CoroutineScope,
    ) : XrpcClientProvider {
        private val mutex = Mutex()

        @Volatile
        private var cachedClient: XrpcClient? = null

        @Volatile
        private var cachedDid: String? = null

        init {
            // Eager cache invalidation on session-DID change. Without this hook,
            // an explicit signOut wouldn't free the cached client until the next
            // authenticated() call lazily detected the DID mismatch — and a
            // second sign-in (as the same DID, after a token-refresh failure
            // forced a re-auth) wouldn't trigger a fresh DPoP keypair install.
            coroutineScope.launch {
                sessionStateProvider.state
                    .map { state -> (state as? SessionState.SignedIn)?.did }
                    .distinctUntilChanged()
                    .collect { newDid ->
                        if (newDid != cachedDid) {
                            invalidate()
                        }
                    }
            }
        }

        override suspend fun authenticated(): XrpcClient =
            mutex.withLock {
                val activeDid =
                    (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                        ?: throw NoSessionException()
                val existing = cachedClient
                if (existing != null && cachedDid == activeDid) {
                    Timber.tag(TAG).d("authenticated() cache HIT did=%s", activeDid.redactDid())
                    return@withLock existing
                }
                Timber.tag(TAG).d("authenticated() cache MISS did=%s — creating fresh client", activeDid.redactDid())
                val freshClient = atOAuth.createClient()
                cachedClient = freshClient
                cachedDid = activeDid
                freshClient
            }

        // Volatile-write pair: a parallel authenticated() under the mutex may
        // immediately overwrite these with a freshly-created client. That's
        // fine — the new client matches the current session DID, and the
        // invalidate() call was racing the session change anyway.
        internal fun invalidate() {
            cachedClient = null
            cachedDid = null
        }

        private companion object {
            const val TAG = "XrpcClientProvider"
        }
    }
