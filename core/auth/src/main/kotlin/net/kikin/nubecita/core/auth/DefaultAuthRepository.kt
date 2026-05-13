package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import net.kikin.nubecita.core.common.session.SessionClearable
import timber.log.Timber
import javax.inject.Inject

internal class DefaultAuthRepository
    @Inject
    constructor(
        private val atOAuth: AtOAuth,
        private val sessionStateProvider: SessionStateProvider,
        private val sessionClearables: Set<@JvmSuppressWildcards SessionClearable>,
    ) : AuthRepository {
        override suspend fun beginLogin(handle: String): Result<String> =
            runCatching { atOAuth.beginLogin(handle) }
                .onFailure { Timber.tag(TAG).e(it, "beginLogin('%s') failed", handle) }

        override suspend fun completeLogin(redirectUri: String): Result<Unit> =
            runCatching {
                atOAuth.completeLogin(redirectUri)
                sessionStateProvider.refresh()
            }.onFailure {
                // Strip the query string before logging — the redirect URI
                // carries the one-time-use OAuth `code` and the CSRF `state`
                // value, neither of which belong in any log surface (logcat
                // today, hypothetical future remote crash reporter tomorrow).
                Timber.tag(TAG).e(it, "completeLogin('%s') failed", redirectUri.substringBefore('?'))
            }

        override suspend fun signOut(): Result<Unit> =
            runCatching {
                // Drop session-scoped in-memory state before revocation.
                // Even if the network logout fails below, each clearable stays
                // cleared — there's no value in retaining optimistic state
                // across a sign-out attempt (the user has indicated they want out).
                sessionClearables.forEach { it.clearSession() }
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
