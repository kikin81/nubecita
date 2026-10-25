package net.kikin.nubecita.core.auth

import android.app.ActivityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.TokenRefresh
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import net.kikin.nubecita.core.auth.di.AuthCoroutineScope
import net.kikin.nubecita.core.logging.CrashReporter
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Non-fatal for a refresh POST whose response was lost after it was sent. The
 * server may have consumed the refresh token; if it did, the client's next
 * refresh replays a dead token and the server deletes the session. This is the
 * precursor event of a spurious logout, recorded where it happens rather than
 * where the logout later surfaces.
 */
internal class TokenRefreshOutcomeUnknownException(
    cause: Throwable,
) : RuntimeException("Token refresh sent but its response was lost — refresh token may be consumed", cause)

/** Whether this process is in the foreground right now. Fakeable in tests. */
internal interface ProcessForegroundSignal {
    fun isForegrounded(): Boolean
}

/**
 * Reads the process importance directly, so it works from any thread and in a
 * worker-only process where no activity ever started — unlike
 * `ProcessLifecycleOwner`, which `:core:auth` doesn't depend on.
 */
internal class ActivityManagerForegroundSignal
    @Inject
    constructor() : ProcessForegroundSignal {
        override fun isForegrounded(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

/** One refresh POST as [TokenRefreshMonitor] observed it. */
internal data class TokenRefreshAttempt(
    val caller: TokenRefreshCaller,
    val outcome: TokenRefreshOutcome,
    val foreground: Boolean,
    val durationMillis: Long,
    val epochMillis: Long,
    /** The token endpoint's `error_description`, when it rejected the token. */
    val errorDescription: String?,
)

/**
 * What the session-cleared non-fatal needs to know about prior refreshes.
 * Split out from [TokenRefreshTelemetry] so [SessionTelemetry] tests can fake it.
 */
internal interface RefreshForensics {
    /** PII-free Crashlytics custom keys describing the refreshes leading up to a clear. */
    suspend fun clearKeys(): Map<String, String>
}

/**
 * Records every refresh-token POST (nubecita-z2gt): a Crashlytics breadcrumb,
 * a GA4 `token_refresh` event, a non-fatal for each lost response, and a
 * persisted history that survives process death for [clearKeys].
 *
 * Purely observational and exception-isolated: it runs inside the SDK's
 * refresh call, and a broken telemetry backend must never fail a refresh.
 */
@Singleton
internal class TokenRefreshTelemetry
    @Inject
    constructor(
        private val crashReporter: CrashReporter,
        private val analytics: AnalyticsClient,
        private val history: RefreshHistoryStore,
        private val foreground: ProcessForegroundSignal,
        @AuthCoroutineScope private val scope: CoroutineScope,
        private val clock: Clock,
    ) : RefreshForensics {
        // The attempt the SDK is about to act on. When that attempt is the
        // invalid_grant that clears the session, this is how the clear names
        // its own trigger — the persisted history only holds earlier refreshes.
        @Volatile
        private var lastAttempt: TokenRefreshAttempt? = null

        fun onAttempt(
            caller: TokenRefreshCaller,
            outcome: TokenRefreshOutcome,
            durationMillis: Long,
            errorDescription: String? = null,
            cause: Throwable? = null,
        ) {
            try {
                val attempt =
                    TokenRefreshAttempt(
                        caller = caller,
                        outcome = outcome,
                        foreground = foreground.isForegrounded(),
                        durationMillis = durationMillis,
                        epochMillis = clock.now().toEpochMilliseconds(),
                        errorDescription = errorDescription?.take(MAX_DESCRIPTION_LENGTH),
                    )
                lastAttempt = attempt
                crashReporter.log(
                    "[$TAG] caller=${caller.wire} outcome=${outcome.wire} " +
                        "fg=${attempt.foreground} ms=$durationMillis",
                )
                analytics.log(TokenRefresh(caller = caller, outcome = outcome, foreground = attempt.foreground))
                if (outcome == TokenRefreshOutcome.LostResponse && cause != null) {
                    crashReporter.setCustomKey(KEY_LOST_REFRESH_CALLER, caller.wire)
                    crashReporter.recordException(TokenRefreshOutcomeUnknownException(cause))
                }
                persist(attempt)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "token-refresh telemetry failed")
            }
        }

        // Off the refresh path: the SDK still has to persist the rotated
        // session after this returns, and a telemetry write must not widen
        // that window.
        private fun persist(attempt: TokenRefreshAttempt) {
            val record = RefreshRecord(epochMillis = attempt.epochMillis, caller = attempt.caller)
            when (attempt.outcome) {
                TokenRefreshOutcome.Ok -> scope.launch { safely { history.recordOk(record) } }
                TokenRefreshOutcome.LostResponse -> scope.launch { safely { history.recordLostResponse(record) } }
                else -> Unit
            }
        }

        override suspend fun clearKeys(): Map<String, String> {
            val now = clock.now().toEpochMilliseconds()
            val past = history.history()
            return buildMap {
                val trigger = lastAttempt?.takeIf { it.outcome == TokenRefreshOutcome.InvalidGrant }
                put("clearing_refresh_caller", trigger?.caller?.wire ?: NONE)
                put("clearing_refresh_fg", trigger?.foreground?.toString() ?: NONE)
                put("clearing_refresh_error", trigger?.errorDescription ?: NONE)
                put("last_ok_refresh_mins_ago", past.lastOk.minutesAgo(now))
                put("last_ok_refresh_caller", past.lastOk?.caller?.wire ?: NONE)
                put("last_lost_refresh_mins_ago", past.lastLostResponse.minutesAgo(now))
                put("last_lost_refresh_caller", past.lastLostResponse?.caller?.wire ?: NONE)
            }
        }

        private fun RefreshRecord?.minutesAgo(now: Long): String = this?.let { ((now - it.epochMillis) / MILLIS_PER_MINUTE).coerceAtLeast(0).toString() } ?: NONE

        private suspend fun safely(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "token-refresh history write failed")
            }
        }

        private companion object {
            const val TAG = "TokenRefresh"
            const val KEY_LOST_REFRESH_CALLER = "lost_refresh_caller"
            const val NONE = "none"
            const val MAX_DESCRIPTION_LENGTH = 80
            const val MILLIS_PER_MINUTE = 60_000L
        }
    }
