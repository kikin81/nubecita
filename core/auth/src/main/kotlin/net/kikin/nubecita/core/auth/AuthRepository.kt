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
}
