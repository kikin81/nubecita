package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import javax.inject.Inject

internal class DefaultAuthRepository
    @Inject
    constructor(
        private val atOAuth: AtOAuth,
        private val sessionStateProvider: SessionStateProvider,
    ) : AuthRepository {
        override suspend fun beginLogin(handle: String): Result<String> = runCatching { atOAuth.beginLogin(handle) }

        override suspend fun completeLogin(redirectUri: String): Result<Unit> =
            runCatching {
                atOAuth.completeLogin(redirectUri)
                sessionStateProvider.refresh()
            }

        override suspend fun signOut(): Result<Unit> =
            runCatching {
                atOAuth.logout()
                sessionStateProvider.refresh()
            }
    }
