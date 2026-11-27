package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSessionStore
import javax.inject.Inject

/**
 * How long the authorization server lets the current session live, which
 * follows from how the client was registered when the session last refreshed.
 */
enum class SessionLifetime {
    /** A public client's session: about two weeks from sign-in, whatever the activity. */
    Public,

    /** A confidential client's session: up to two years from sign-in, and three months from the last refresh. */
    Confidential,
}

/** Tells a consumer (such as a "reconnect before you are signed out" prompt) which lifetime applies. */
interface SessionLifetimeProvider {
    /** The signed-in session's lifetime, or `null` when no one is signed in. */
    suspend fun current(): SessionLifetime?
}

internal class DefaultSessionLifetimeProvider
    @Inject
    constructor(
        private val sessionStore: OAuthSessionStore,
    ) : SessionLifetimeProvider {
        override suspend fun current(): SessionLifetime? {
            val session = sessionStore.load() ?: return null
            // Only the token-mediating backend reports the method. A session it
            // has not yet reported on was last refreshed as a public client.
            return if (session.clientAuthMethod == CONFIDENTIAL_METHOD) SessionLifetime.Confidential else SessionLifetime.Public
        }

        private companion object {
            const val CONFIDENTIAL_METHOD = "private_key_jwt"
        }
    }
