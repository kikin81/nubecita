package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.runtime.XrpcClient

/**
 * Canonical injection surface for an authenticated [XrpcClient].
 *
 * Feature modules and cross-feature data layers that need to make
 * authenticated XRPC calls inject this interface and call
 * [authenticated] — they MUST NOT inject `AtOAuth` directly to call
 * `createClient()` themselves. The default implementation caches the
 * client across calls keyed by the active session DID and invalidates
 * the cache on session change.
 *
 * If no session is persisted when [authenticated] is called, the
 * implementation throws [NoSessionException]. Callers can choose to
 * handle this as an "unauthenticated" error mode (e.g., route to login,
 * show an account-required state) without coupling to the OAuth library
 * directly.
 */
interface XrpcClientProvider {
    /**
     * Returns an authenticated [XrpcClient] for the currently-persisted
     * session. The first call constructs a fresh DPoP-signed client via
     * `AtOAuth.createClient()`; subsequent calls return the cached
     * instance until the active session DID changes (sign-out, refresh
     * failure, sign-in as a different account), at which point a fresh
     * client is created.
     *
     * @throws NoSessionException when no session is persisted.
     */
    suspend fun authenticated(): XrpcClient
}

/**
 * Thrown by [XrpcClientProvider.authenticated] when no session is
 * persisted. Subclass of [IllegalStateException] so callers using
 * `runCatching { ... }` patterns observe it as a typed `Result.failure`
 * without forcing every call site to wrap a `try / catch`.
 */
class NoSessionException(
    message: String = "No authenticated session",
) : IllegalStateException(message)
