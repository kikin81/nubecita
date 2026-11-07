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
     * Begins an OAuth-initiated signup flow against `bsky.social` with
     * OIDC `prompt=create`. Unlike [beginLogin], no handle or DID is
     * required — the auth server is known up-front, discovery is
     * short-circuited, and PAR carries `prompt=create` so the auth
     * server renders its signup UI inside the OAuth roundtrip.
     *
     * The returned URL is opened in a Chrome Custom Tab the same way
     * the login authorization URL is. After signup completes, the
     * standard `net.kikin.nubecita:/oauth-redirect?code=...` redirect
     * lands back at `MainActivity`, flows through `OAuthRedirectBroker`,
     * and is exchanged for tokens by the existing [completeLogin] path
     * — the user comes back signed in to the freshly-minted account
     * with no re-typing of their new handle.
     *
     * @return [Result.success] with the authorization URL on success;
     *   [Result.failure] wrapping the underlying exception on any
     *   failure (network error, malformed metadata,
     *   `OAuthSignupNotSupportedException` if the auth server doesn't
     *   advertise `"create"` in `prompt_values_supported`, etc.).
     */
    suspend fun beginSignup(): Result<String>

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
