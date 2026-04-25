package net.kikin.nubecita.core.auth

import android.util.Log
import io.github.kikin81.atproto.oauth.AtOAuth
import javax.inject.Inject

internal class DefaultAuthRepository
    @Inject
    constructor(
        private val atOAuth: AtOAuth,
        private val sessionStateProvider: SessionStateProvider,
    ) : AuthRepository {
        override suspend fun beginLogin(handle: String): Result<String> =
            runCatching { atOAuth.beginLogin(handle) }
                .onFailure { Log.e(TAG, "beginLogin('$handle') failed", it) }

        override suspend fun completeLogin(redirectUri: String): Result<Unit> =
            runCatching {
                atOAuth.completeLogin(redirectUri)
                sessionStateProvider.refresh()
            }.onFailure { Log.e(TAG, "completeLogin('$redirectUri') failed", it) }

        override suspend fun signOut(): Result<Unit> =
            runCatching {
                atOAuth.logout()
                sessionStateProvider.refresh()
            }.onFailure { Log.e(TAG, "signOut() failed", it) }

        private companion object {
            // Logcat tag stays under 23 chars (Android Log API ceiling) so it shows up
            // unmangled in `adb logcat` and Studio's Logcat panel. Filter via:
            //   adb logcat -s AuthRepository
            const val TAG = "AuthRepository"
        }
    }
