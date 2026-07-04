package net.kikin.nubecita.core.auth

import net.kikin.nubecita.core.logging.CrashReporter
import timber.log.Timber
import javax.inject.Inject

/**
 * Wrapper non-fatal for "the SDK rotated the refresh token but persisting the
 * new session failed even after its retry". The in-memory session stays valid
 * for the process lifetime, but if the process dies before a later save
 * succeeds, the stale persisted token is rejected as reuse (`invalid_grant`)
 * on the next cold start — a silent logout. Grouping these in Crashlytics
 * measures how often that window actually opens (epic nubecita-09xt).
 */
internal class SessionPersistFailedException(
    cause: Throwable,
) : RuntimeException("OAuth session persist failed after token rotation", cause)

/**
 * Bridges the SDK's `AtOAuth(onSessionPersistFailure = …)` callback to
 * Crashlytics. Exception-isolated: the callback fires inside the SDK's
 * refresh path, so a broken telemetry backend must never propagate back
 * into it and fail a refresh that succeeded server-side.
 */
internal class SessionPersistFailureReporter
    @Inject
    constructor(
        private val crashReporter: CrashReporter,
    ) {
        fun report(cause: Throwable) {
            try {
                crashReporter.recordException(SessionPersistFailedException(cause))
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "session-persist-failure telemetry failed")
            }
        }

        private companion object {
            const val TAG = "SessionPersist"
        }
    }
