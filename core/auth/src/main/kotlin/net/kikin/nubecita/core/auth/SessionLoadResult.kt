package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSession

/**
 * App-internal result of reading the persisted session, distinguishing the two
 * cases the SDK's nullable `OAuthSessionStore.load()` collapses: "no session
 * exists" versus "the session exists but this read failed" (disk contention,
 * Keystore transiently unavailable just after boot, AEAD/parse failure).
 *
 * The distinction is the fix for the top spurious-logout mechanism (epic
 * nubecita-09xt): a transient [ReadError] at cold start must be retried while
 * the splash holds, not routed to Login as if the user had signed out.
 */
internal sealed interface SessionLoadResult {
    data class Loaded(
        val session: OAuthSession,
    ) : SessionLoadResult

    data object Absent : SessionLoadResult

    data class ReadError(
        val cause: Throwable,
    ) : SessionLoadResult
}

/**
 * App-internal read seam exposing [SessionLoadResult]. Implemented by
 * `EncryptedOAuthSessionStore` alongside the SDK-facing `OAuthSessionStore`
 * (whose `load()` keeps its degrade-to-null contract for the SDK's own reads).
 */
internal fun interface SessionReader {
    suspend fun loadResult(): SessionLoadResult
}
