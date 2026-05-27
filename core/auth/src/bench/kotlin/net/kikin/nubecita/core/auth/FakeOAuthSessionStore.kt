package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [OAuthSessionStore]. Always reports "no session"
 * — [load] returns `null`, [save] and [clear] are no-ops.
 *
 * The bench journey doesn't go through the auth flow, so this store
 * is never read in practice. [FakeSessionStateProvider] short-circuits
 * the only legitimate `load()` caller in the routing path. A more
 * faithful in-memory implementation would synthesize a hardcoded
 * `OAuthSession` here for parity with [FakeSessionStateProvider]'s
 * `SignedIn` state, but constructing one requires DPoP key material +
 * a wire-shaped access token that we'd never use; the simpler "no
 * session" shape keeps the Hilt graph valid without introducing
 * cryptographic stubs.
 *
 * Scoped `@Singleton` to match the production binding.
 */
@Singleton
internal class FakeOAuthSessionStore
    @Inject
    constructor() : OAuthSessionStore {
        override suspend fun load(): OAuthSession? = null

        override suspend fun save(session: OAuthSession) = Unit

        override suspend fun clear() = Unit
    }
