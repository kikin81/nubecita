package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import kotlinx.coroutines.CancellationException
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
                .onFailure {
                    // runCatching on a suspend fn also catches CancellationException —
                    // rethrow so structured cancellation isn't swallowed into a Result.
                    if (it is CancellationException) throw it
                    // The handle is user-provided PII; log only the throwable identity,
                    // matching the redaction discipline in the profile/actor repos.
                    Timber.tag(TAG).w(it, "beginLogin failed")
                }

        override suspend fun beginSignup(): Result<String> =
            // Pin the auth server and the prompt=create capability gate explicitly
            // rather than leaning on the library defaults: signup is only wired for
            // bsky.social today, and requiring the server to advertise `create` in
            // `prompt_values_supported` keeps us from opening a Custom Tab onto an
            // entryway that can't honor the flow. Matches these to the AtOAuth
            // defaults so an upstream default change can't silently drift behavior.
            runCatching {
                atOAuth.beginSignup(
                    authServer = "bsky.social",
                    requirePromptCreateSupport = true,
                )
            }.onFailure { Timber.tag(TAG).e(it, "beginSignup() failed") }

        override suspend fun completeLogin(redirectUri: String): Result<Unit> =
            runCatching {
                atOAuth.completeLogin(redirectUri)
                sessionStateProvider.refresh()
            }.onFailure {
                if (it is CancellationException) throw it
                // Strip the query string before logging — the redirect URI
                // carries the one-time-use OAuth `code` and the CSRF `state`
                // value, neither of which belong in any log surface (logcat
                // today, hypothetical future remote crash reporter tomorrow).
                Timber.tag(TAG).w(it, "completeLogin('%s') failed", redirectUri.substringBefore('?'))
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
            }.onFailure {
                if (it is CancellationException) throw it
                Timber.tag(TAG).w(it, "signOut() failed")
            }

        private companion object {
            // Logcat tag stays under 23 chars (Android Log API ceiling) so it shows up
            // unmangled in `adb logcat` and Studio's Logcat panel. Filter via:
            //   adb logcat -s AuthRepository
            const val TAG = "AuthRepository"
        }
    }
