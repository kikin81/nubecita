package net.kikin.nubecita.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AuthKeysetRegenerated
import net.kikin.nubecita.core.analytics.SessionClearReason
import net.kikin.nubecita.core.analytics.SessionCleared
import net.kikin.nubecita.core.analytics.SessionReadError
import net.kikin.nubecita.core.analytics.SessionReadErrorCause
import net.kikin.nubecita.core.analytics.SessionReadErrorTerminal
import net.kikin.nubecita.core.logging.CrashReporter
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Marker non-fatal recorded on every session `clear()`. Constructed at the top
 * of [EncryptedOAuthSessionStore.clear] — before any suspension point — so its
 * stack still carries the caller frames (the SDK's `DpopAuthProvider.failRefresh`
 * on `invalid_grant`, `AtOAuth.logout` on user sign-out). The message is static;
 * no token, handle, or DID ever rides on it.
 */
internal class SessionClearedException : RuntimeException("OAuth session cleared")

/**
 * Wrapper non-fatal for session read failures, so Crashlytics groups all
 * read-path degradations under one issue while preserving the real [cause].
 */
internal class SessionReadFailedException(
    cause: Throwable,
) : RuntimeException("OAuth session read failed (degraded to no-session)", cause)

/**
 * Wrapper non-fatal for a read failure that survived every bounded retry —
 * the user was routed to Login with a session still on disk. The
 * user-impacting subset of [SessionReadFailedException]'s per-attempt count.
 */
internal class SessionReadTerminalException(
    cause: Throwable,
) : RuntimeException("OAuth session read failed after all retries — routed to Login", cause)

/**
 * Wrapper non-fatal for a destructive Tink keyset regeneration — every fire is
 * a guaranteed silent logout (the old session ciphertext becomes undecryptable).
 */
internal class AuthKeysetRegeneratedException(
    cause: GeneralSecurityException,
) : RuntimeException("Tink session keyset regenerated — persisted session lost", cause)

/**
 * Session-loss telemetry (epic nubecita-09xt): every persisted-session wipe and
 * every read-path degradation becomes a Crashlytics non-fatal + a GA4 event, so
 * spurious-logout mechanisms are measurable in the wild. Purely observational —
 * never throws into the store paths it instruments.
 */
internal class SessionTelemetry
    @Inject
    constructor(
        private val crashReporter: CrashReporter,
        private val analytics: AnalyticsClient,
        private val loginTimestamps: LoginTimestampStore,
        private val clock: Clock,
    ) {
        // Each method is exception-isolated: a broken telemetry backend (or an
        // unreadable timestamp store) must never disrupt the auth path it
        // observes — load()'s degrade-to-null guarantee, clear()/sign-out, and
        // the keyset recovery would all otherwise inherit the throw.

        suspend fun onSessionCleared(marker: SessionClearedException) {
            try {
                val reason = bucketClearReason(marker)
                crashReporter.setCustomKey("session_clear_reason", reason.wire)
                crashReporter.recordException(marker)
                analytics.log(SessionCleared(reason = reason, daysSinceLogin = daysSinceLogin()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "session-cleared telemetry failed")
            }
        }

        fun onSessionReadError(cause: Throwable) {
            try {
                crashReporter.recordException(SessionReadFailedException(cause))
                analytics.log(SessionReadError(cause = bucketReadErrorCause(cause)))
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "session-read-error telemetry failed")
            }
        }

        fun onSessionReadErrorTerminal(cause: Throwable) {
            try {
                crashReporter.recordException(SessionReadTerminalException(cause))
                analytics.log(SessionReadErrorTerminal(cause = bucketReadErrorCause(cause)))
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "terminal session-read-error telemetry failed")
            }
        }

        fun onKeysetRegenerated(cause: GeneralSecurityException) {
            try {
                crashReporter.recordException(AuthKeysetRegeneratedException(cause))
                analytics.log(AuthKeysetRegenerated)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "keyset-regenerated telemetry failed")
            }
        }

        private suspend fun daysSinceLogin(): Long? {
            val lastLogin = loginTimestamps.lastLoginEpochMillis() ?: return null
            val elapsedMillis = clock.now().toEpochMilliseconds() - lastLogin
            return (elapsedMillis / MILLIS_PER_DAY).coerceAtLeast(0)
        }

        /**
         * Bucket by the frames on the marker's stack. The two known callers of
         * `clear()` are the SDK's `DpopAuthProvider.failRefresh` (refresh token
         * rejected with `invalid_grant`) and `AtOAuth.logout` (user sign-out via
         * `DefaultAuthRepository.signOut`). The full stack rides on the recorded
         * non-fatal regardless, so a mis-bucket is recoverable in Crashlytics.
         */
        private fun bucketClearReason(marker: SessionClearedException): SessionClearReason {
            val classNames = marker.stackTrace.map { it.className }
            return when {
                classNames.any { it.contains("DpopAuthProvider") } -> SessionClearReason.InvalidGrant
                classNames.any { it.contains("AtOAuth") } -> SessionClearReason.UserSignOut
                else -> SessionClearReason.Unknown
            }
        }

        private fun bucketReadErrorCause(cause: Throwable): SessionReadErrorCause =
            when (cause) {
                is IOException -> SessionReadErrorCause.Io
                is GeneralSecurityException -> SessionReadErrorCause.Security
                is SerializationException -> SessionReadErrorCause.Serialization
                // The read path only swallows the three types above; anything else
                // propagates before telemetry runs. Io is the conservative bucket.
                else -> SessionReadErrorCause.Io
            }

        private companion object {
            const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
            const val TAG = "SessionTelemetry"
        }
    }
