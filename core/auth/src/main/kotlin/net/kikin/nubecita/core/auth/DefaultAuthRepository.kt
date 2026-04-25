package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import javax.inject.Inject

internal class DefaultAuthRepository
    @Inject
    constructor(
        private val atOAuth: AtOAuth,
    ) : AuthRepository {
        override suspend fun beginLogin(handle: String): Result<String> = runCatching { atOAuth.beginLogin(handle) }

        override suspend fun completeLogin(redirectUri: String): Result<Unit> = runCatching { atOAuth.completeLogin(redirectUri) }
    }
