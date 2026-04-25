package net.kikin.nubecita.core.auth

/**
 * Narrow domain seam over `atproto-oauth`'s `AtOAuth` flow orchestrator.
 *
 * Consumers (login screen, splash routing, sign-out flow) inject this
 * interface rather than `AtOAuth` directly so they can be unit-tested
 * with a fake. The concrete implementation in `:core:auth` delegates to
 * `AtOAuth` and wraps its exceptions as [Result.failure].
 *
 * Today this surface only exposes [beginLogin]; `completeLogin`,
 * `currentSession`, and `signOut` will be added under the next OAuth
 * milestones (`nubecita-ck0`, `nubecita-30c`).
 */
interface AuthRepository {
    /**
     * Resolves [handle], performs PAR with PKCE + DPoP, and returns the
     * authorization URL the caller should open in a Custom Tab.
     *
     * @return [Result.success] with the authorization URL on success;
     *   [Result.failure] wrapping the underlying exception on any failure
     *   (handle resolution, PAR rejection, network error, etc.).
     */
    suspend fun beginLogin(handle: String): Result<String>

    /**
     * Exchanges the OAuth authorization code in [redirectUri] for access
     * + refresh tokens via the token endpoint and persists the resulting
     * `OAuthSession` through the bound `OAuthSessionStore`. Carries no
     * payload because the session is reachable through the store after
     * success.
     *
     * On success, also triggers a [SessionStateProvider.refresh] so
     * reactive consumers (e.g. `MainActivity`'s splash routing) transition
     * to [SessionState.SignedIn] automatically.
     *
     * @return [Result.success] with [Unit] on a clean exchange;
     *   [Result.failure] wrapping the underlying exception on malformed
     *   URI, missing PKCE state, server rejection, etc.
     */
    suspend fun completeLogin(redirectUri: String): Result<Unit>

    /**
     * Revokes the current session at the authorization server's
     * revocation endpoint and clears the local `OAuthSessionStore`. On
     * success, also triggers a [SessionStateProvider.refresh] so reactive
     * consumers transition to [SessionState.SignedOut] automatically.
     *
     * If the network revocation call fails, the local session is *not*
     * cleared either — failures propagate as [Result.failure] so callers
     * can choose retry / force-clear / surface-error behavior. (Force-
     * clear is a future affordance for a settings screen.)
     *
     * @return [Result.success] with [Unit] on a clean revocation;
     *   [Result.failure] wrapping the underlying exception on network
     *   error, revocation endpoint rejection, etc.
     */
    suspend fun signOut(): Result<Unit>
}
