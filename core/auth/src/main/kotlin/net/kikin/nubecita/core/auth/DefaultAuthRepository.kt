package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import timber.log.Timber
import javax.inject.Inject

internal class DefaultAuthRepository
    @Inject
    constructor(
        private val atOAuth: AtOAuth,
        private val sessionStateProvider: SessionStateProvider,
    ) : AuthRepository {
        override suspend fun beginLogin(handle: String): Result<String> =
            runCatching { atOAuth.beginLogin(handle) }
                .onFailure { Timber.tag(TAG).e(it, "beginLogin('%s') failed", handle) }

        override suspend fun completeLogin(redirectUri: String): Result<Unit> =
            runCatching {
                atOAuth.completeLogin(redirectUri)
                sessionStateProvider.refresh()
            }.onFailure { Timber.tag(TAG).e(it, "completeLogin('%s') failed", redirectUri) }

        override suspend fun signOut(): Result<Unit> =
            runCatching {
                atOAuth.logout()
                sessionStateProvider.refresh()
            }.onFailure { Timber.tag(TAG).e(it, "signOut() failed") }

        private companion object {
            // Logcat tag stays under 23 chars (Android Log API ceiling) so it shows up
            // unmangled in `adb logcat` and Studio's Logcat panel. Filter via:
            //   adb logcat -s AuthRepository
            const val TAG = "AuthRepository"
        }
    }
