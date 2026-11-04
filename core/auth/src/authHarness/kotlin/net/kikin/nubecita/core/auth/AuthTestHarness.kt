package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.kikin.nubecita.core.analytics.SessionClearReason
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in harness for exercising the session-loss telemetry chain against a
 * real PDS on a real build (nubecita-zh7p).
 *
 * The session-loss instrumentation has never been verified end to end: a
 * genuine spurious logout on 1.365.2 produced no Crashlytics non-fatal and no
 * GA4 event, which is indistinguishable from "the pipeline is broken". Reading
 * the code found one mechanism that explains it (nubecita-yv4p), but reading is
 * not proof that what remains actually reports.
 *
 * Rather than fake a failure, this poisons the stored credential and lets the
 * real machinery react: the SDK performs a real refresh POST to the real token
 * endpoint, the server rejects it for real, and `DpopAuthProvider.failRefresh`
 * drives the same `clear()` a genuine logout would. Nothing here stands in for
 * app code — the only lie told is the token itself.
 *
 * ## Availability
 *
 * This file lives in the `authHarness` source set, which is only attached to
 * the compile when the build asks for it. In every other build the class does
 * not exist at all — there is nothing to strip and nothing to verify. Build
 * with:
 *
 * ```
 * ./gradlew :app:assembleProductionRelease -PdebugSignedRelease=true -PauthTestHarness=true
 * ```
 */
@Singleton
class AuthTestHarness
    @Inject
    internal constructor(
        private val dataStore: DataStore<OAuthSession?>,
        private val sessionStore: OAuthSessionStore,
        private val telemetry: SessionTelemetry,
        private val clearPending: SessionClearPendingStore,
    ) {
        /**
         * The failure to stage. Each one ends with the process being halted,
         * because every path under test only completes on the NEXT launch — the
         * refresh that rejects, or the recovery pass that reports.
         */
        enum class Scenario(
            val label: String,
            val expectation: String,
            /**
             * True when the scenario reports through the normal telemetry path
             * before the process is halted, so the halt has to wait for that
             * report to reach disk.
             */
            val recordsTelemetryInline: Boolean = false,
        ) {
            /**
             * The main event. Poisons both tokens: the bad access token makes the
             * next XRPC call 401, which drives a real refresh, which the server
             * rejects as the refresh token is garbage.
             */
            BadRefreshToken(
                label = "Bad refresh token (real invalid_grant)",
                expectation = "token_refresh nonce_challenge then invalid_grant, session_cleared, signed out",
            ),

            /**
             * Control. Only the access token is poisoned, so the refresh runs with
             * a good refresh token and SUCCEEDS. Proves the refresh telemetry path
             * works without destroying the session — if this one reports nothing,
             * the problem is the pipeline, not the clear path.
             */
            BadAccessToken(
                label = "Bad access token (refresh should succeed)",
                expectation = "token_refresh outcome=ok, still signed in, no session_cleared",
            ),

            /**
             * Stages the exact on-disk state a clear that died mid-flight leaves
             * behind: pending marker written, session gone, nothing reported.
             * Validates the nubecita-yv4p recovery pass, which is new and has
             * never run on a device.
             */
            ProcessDeathMidClear(
                label = "Process death mid-clear (recovery pass)",
                expectation = "session_clear_telemetry_lost on next launch, signed out",
            ),

            /**
             * Baseline. The ordinary clear path, reported inline. No refresh runs,
             * so there are no TokenRefresh breadcrumbs — which is itself worth
             * seeing next to the others.
             */
            DirectClear(
                label = "Direct clear (no refresh)",
                expectation = "session_cleared with reason=unknown, no refresh breadcrumbs",
                recordsTelemetryInline = true,
            ),
        }

        /**
         * Stages [scenario] and halts the process. Does not return.
         *
         * [Runtime.halt] rather than a graceful exit: shutdown hooks would give
         * the app a chance to flush things a real kill would not, and the whole
         * point is to reproduce an abrupt death. Every write above it has already
         * returned from a suspending DataStore call, so it is durably on disk.
         */
        suspend fun runAndHalt(scenario: Scenario) {
            Timber.tag(TAG).i("staging %s", scenario.name)
            when (scenario) {
                Scenario.BadRefreshToken -> poison(accessToken = POISON, refreshToken = POISON)
                Scenario.BadAccessToken -> poison(accessToken = POISON, refreshToken = null)
                Scenario.ProcessDeathMidClear -> stageInterruptedClear()
                Scenario.DirectClear -> sessionStore.clear()
            }
            // Crashlytics persists a recordException asynchronously, so halting
            // the instant clear() returns kills the process before the report
            // reaches disk — the harness destroys the very evidence it exists to
            // capture. Only DirectClear records telemetry inline; the others
            // halt before anything is reported, by design, and pay nothing for
            // the wait. A real logout is never followed by an immediate kill,
            // so this delay makes the scenario MORE faithful, not less.
            if (scenario.recordsTelemetryInline) {
                Timber.tag(TAG).i("staged %s — letting telemetry persist", scenario.name)
                delay(TELEMETRY_PERSIST_DELAY_MILLIS)
            }
            Timber.tag(TAG).i("staged %s — halting", scenario.name)
            halt()
        }

        /**
         * Rewrites the stored tokens, leaving every other field — DPoP keypair,
         * PDS URL, token endpoint, nonces — untouched, so the refresh the SDK
         * sends afterwards is a well-formed request that fails for exactly one
         * reason: the credential is not valid.
         *
         * A null [refreshToken] leaves the real one in place.
         */
        private suspend fun poison(
            accessToken: String,
            refreshToken: String?,
        ) {
            dataStore.updateData { current ->
                current?.let { session ->
                    session.copy(
                        accessToken = accessToken,
                        refreshToken = refreshToken ?: session.refreshToken,
                    )
                }
            }
        }

        /**
         * Writes the pending marker as an `invalid_grant` clear, wipes the
         * session, and records nothing — the state a clear leaves behind when the
         * process dies between the two. Marked as InvalidGrant directly because
         * the real bucketing reads the caller's stack frames, which a harness
         * call cannot honestly produce.
         */
        private suspend fun stageInterruptedClear() {
            clearPending.markPending(SessionClearReason.InvalidGrant)
            try {
                dataStore.updateData { null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The wipe is what makes the marker honest. If it throws, the
                // session is still there and the marker would make the next
                // launch report a logout that never happened — the harness
                // would manufacture the exact false positive it exists to
                // detect, and the run would read as a clean pass. Roll the
                // marker back so the failure is visible instead.
                clearPending.clearPending()
                throw e
            }
        }

        internal fun halt(): Nothing {
            Runtime.getRuntime().halt(0)
            error("unreachable: halt() does not return")
        }

        private companion object {
            const val TAG = "AuthTestHarness"

            /**
             * Shaped like a token so nothing upstream rejects it for being
             * malformed before it reaches the server, and labelled so it is
             * obvious in a capture that this was deliberate.
             */
            const val POISON = "nubecita-harness-deliberately-invalid-token"

            /**
             * Generous on purpose: this runs once, by hand, and a report lost to
             * an impatient halt costs a whole re-run plus a fresh sign-in.
             */
            const val TELEMETRY_PERSIST_DELAY_MILLIS = 3_000L
        }
    }
