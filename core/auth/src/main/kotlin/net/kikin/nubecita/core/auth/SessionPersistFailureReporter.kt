package net.kikin.nubecita.core.auth

import kotlinx.coroutines.CancellationException
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
 * Crashlytics. The callback fires inside the SDK's refresh path, so a failing
 * telemetry backend (any [Exception]; JVM [Error]s still propagate) must never
 * bubble back into it and fail a refresh that succeeded server-side.
 */
internal class SessionPersistFailureReporter
    @Inject
    constructor(
        private val crashReporter: CrashReporter,
    ) {
        fun report(cause: Throwable) {
            // The SDK rethrows cooperative cancellation before invoking the
            // callback, but guard anyway: a cancelled save is flow control,
            // not a persistence failure — recording it would pollute the
            // signal this non-fatal exists to measure.
            if (cause is CancellationException) return
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
