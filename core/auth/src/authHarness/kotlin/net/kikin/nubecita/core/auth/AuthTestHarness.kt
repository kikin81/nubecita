package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.kikin.nubecita.core.analytics.SessionClearReason
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
        private val refreshHistory: RefreshHistoryStore,
        private val harnessStore: AuthHarnessStore,
    ) {
        /**
         * The failure to stage. Most end with the process being halted, because
         * the path under test only completes on the NEXT launch — the refresh
         * that rejects, or the recovery pass that reports.
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
            /**
             * False for a scenario that only prepares something and must leave
             * the app running — [StashAccessToken] needs the session intact and
             * the process alive so the stashed token can age.
             */
            val halts: Boolean = true,
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
             * Exercises the SDK's `invalid_token` branch, NOT real expiry.
             *
             * The poison string is opaque, so `accessTokenExpiry()` returns null
             * and `isAccessTokenExpired()` is false. With a stale DPoP nonce the
             * first 401 carries only `use_dpop_nonce`, so `onUnauthorized`
             * stores the nonce and returns; the retry then gets `invalid_token`,
             * and `XrpcClient` retries only once, so THAT request can fail. The
             * failure is a harness artifact of an opaque token, not a
             * regression. Use [ExpiredAccessToken] for the path real users hit.
             */
            BadAccessToken(
                label = "Bad access token (invalid_token branch; may fail one request)",
                expectation = "refresh succeeds, still signed in; one request may 4xx — see KDoc",
            ),

            /**
             * Step 1 of real expiry. Saves the CURRENT access token — a real,
             * server-signed JWT bound to the live DPoP key — and logs its `exp`.
             * Does not touch the session and does not halt: the app keeps running
             * and the token keeps being used normally until it ages out.
             */
            StashAccessToken(
                label = "Stash the live access token (step 1 of 2)",
                expectation = "logs the token's exp and the wait remaining; changes nothing",
                halts = false,
            ),

            /**
             * Step 2 of real expiry. Writes the stashed token back once its `exp`
             * has passed, leaving the refresh token, DPoP key and nonces alone.
             * Refuses while the token is still valid.
             *
             * This is the only scenario that reproduces what a real idling user
             * hits: `isAccessTokenExpired()` is true, so the first 401 of the
             * cold-start burst refreshes single-flight before its one retry.
             */
            ExpiredAccessToken(
                label = "Genuinely expired access token (step 2 of 2)",
                expectation = "exactly one token_refresh outcome=ok after its nonce_challenge, still signed in",
            ),

            /**
             * Stages the in-flight refresh marker a process leaves behind when it
             * dies after sending the POST but before saving the response — the
             * now-proven cause of the remaining MIUI logouts (nubecita-25a5).
             * Costs no real token: nothing is sent, only the marker is written.
             */
            DiedInFlightMarker(
                label = "In-flight refresh marker from a dead process",
                expectation = "TokenRefreshDiedInFlightException and token_refresh outcome=process_died next launch",
            ),

            /**
             * Stages the exact on-disk state a clear that died mid-flight leaves
             * behind: pending marker written, session gone, nothing reported.
             * Validates the nubecita-yv4p recovery pass.
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
         * Stages [scenario]. Halts the process afterwards unless the scenario
         * declares otherwise, so for most scenarios this does not return.
         *
         * [Runtime.halt] rather than a graceful exit: shutdown hooks would give
         * the app a chance to flush things a real kill would not, and the whole
         * point is to reproduce an abrupt death. Every write above it has already
         * returned from a suspending DataStore call, so it is durably on disk.
         */
        suspend fun run(scenario: Scenario) {
            Timber.tag(TAG).i("staging %s", scenario.name)
            when (scenario) {
                Scenario.BadRefreshToken -> poison(accessToken = POISON, refreshToken = POISON)
                Scenario.BadAccessToken -> poison(accessToken = POISON, refreshToken = null)
                Scenario.StashAccessToken -> stashAccessToken()
                Scenario.ExpiredAccessToken -> applyStashedAccessToken()
                Scenario.DiedInFlightMarker -> stageDiedInFlightMarker()
                Scenario.ProcessDeathMidClear -> stageInterruptedClear()
                Scenario.DirectClear -> sessionStore.clear()
            }
            if (!scenario.halts) {
                Timber.tag(TAG).i("staged %s — leaving the process running", scenario.name)
                return
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
         * Saves the live access token and reports when it will expire.
         *
         * Fails loudly when the token is not a JWT carrying `exp`. That is not
         * defensive padding: the SDK derives expiry solely by decoding the
         * token's payload, so against a PDS that issues opaque access tokens
         * `isAccessTokenExpired()` is permanently false, the expiry path does
         * not exist, and [Scenario.ExpiredAccessToken] cannot work. Better to
         * say so here than to stage something that silently tests the wrong
         * branch.
         */
        private suspend fun stashAccessToken() {
            val session = currentSession()
            val exp =
                jwtExpiry(session.accessToken)
                    ?: error(
                        "the access token is not a JWT with an exp claim, so the SDK can never see it as " +
                            "expired and ExpiredAccessToken cannot work against this PDS — the stash was " +
                            "NOT saved. Re-run the plan with this finding.",
                    )
            harnessStore.stash(accessToken = session.accessToken, expEpochSeconds = exp)
            val secondsLeft = exp - sdkNowSeconds(session)
            Timber.tag(TAG).w(
                "stashed the live access token: exp=%d, %d seconds (%d min) until the SDK calls it expired",
                exp,
                secondsLeft,
                secondsLeft / SECONDS_PER_MINUTE,
            )
        }

        /**
         * Writes the stashed token back over the live one, leaving the refresh
         * token, DPoP keypair and nonces in place, so the session holds a
         * genuinely expired access token next to a valid refresh token.
         *
         * Refuses while the token would still be accepted. The SDK treats a
         * token as expired 30 s before `exp`; this demands `exp` itself be past,
         * which is strictly stronger, so a scenario that gets this far is one
         * the SDK is certain to refresh.
         */
        private suspend fun applyStashedAccessToken() {
            val session = currentSession()
            val stashed = harnessStore.stashed() ?: error("nothing stashed — run StashAccessToken first")
            val secondsLeft = stashed.expEpochSeconds - sdkNowSeconds(session)
            if (secondsLeft > 0) {
                error(
                    "the stashed token does not expire for another $secondsLeft seconds " +
                        "(${secondsLeft / SECONDS_PER_MINUTE} min); wait that out and re-run",
                )
            }
            dataStore.updateData { current ->
                current?.let { it.copy(accessToken = stashed.accessToken) }
            }
            // Applied once and then gone: leaving a live-looking credential on
            // disk past its single use is pointless exposure, and a stale stash
            // is how the next run silently applies the wrong token.
            harnessStore.clear()
            Timber.tag(TAG).w(
                "applied the stashed access token, expired %d seconds ago; refresh token left untouched",
                -secondsLeft,
            )
        }

        /**
         * Writes the in-flight marker and nothing else — the state a process
         * leaves when it dies between sending the refresh POST and saving the
         * response. `foreground = false` and an untagged caller because that is
         * the shape the MIUI logouts actually took: a background-woken process
         * that refreshed and was frozen moments later.
         *
         * The fabricated process id is prefixed so a reader of the stored marker
         * can see it was staged. Note that `reportRefreshDiedInFlight` puts only
         * the CALLER on the non-fatal and only caller/outcome/foreground on the
         * GA4 event — the process id never reaches either, so it cannot be used
         * to filter the staged events out downstream.
         */
        private suspend fun stageDiedInFlightMarker() {
            val marker =
                InFlightRefresh(
                    epochMillis = System.currentTimeMillis() - MARKER_BACKDATE_MILLIS,
                    caller = TokenRefreshCaller.Untagged,
                    foreground = false,
                    processId = "$HARNESS_PROCESS_ID_PREFIX${UUID.randomUUID()}",
                )
            refreshHistory.markInFlight(marker)
            Timber.tag(TAG).w(
                "staged an in-flight marker (processId=%s). Next launch will report " +
                    "TokenRefreshDiedInFlightException and token_refresh outcome=process_died with " +
                    "refresh_caller=%s, foreground=%b. The process id is NOT reported — exclude these " +
                    "from any fleet count by device and run time.",
                marker.processId,
                marker.caller.wire,
                marker.foreground,
            )
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

        private suspend fun currentSession(): OAuthSession =
            dataStore.data.firstOrNull()
                ?: error("no stored session — sign in before running the harness")

        /**
         * The clock the SDK compares `exp` against: wall time shifted by the
         * offset it calibrated from the server's `Date` header. Using plain wall
         * time here would let this disagree with `isAccessTokenExpired()` on a
         * device whose clock is off, which is exactly the case the offset exists
         * for.
         */
        private fun sdkNowSeconds(session: OAuthSession): Long = (System.currentTimeMillis() / MILLIS_PER_SECOND) + session.clockOffsetSeconds

        /**
         * The `exp` claim of a JWT access token, or null when the token is
         * opaque or malformed. Mirrors `DpopAuthProvider.accessTokenExpiry`
         * deliberately — a different decoding here could call a token expired
         * that the SDK does not, and the point is to agree with the SDK.
         */
        @OptIn(ExperimentalEncodingApi::class)
        private fun jwtExpiry(token: String): Long? =
            try {
                val parts = token.split('.')
                if (parts.size != JWT_PART_COUNT) {
                    null
                } else {
                    val payload = Base64.UrlSafe.decode(parts[1].padBase64()).toString(Charsets.UTF_8)
                    JSON
                        .parseToJsonElement(payload)
                        .jsonObject["exp"]
                        ?.jsonPrimitive
                        ?.long
                }
            } catch (_: Exception) {
                null
            }

        private fun String.padBase64(): String =
            when (length % BASE64_BLOCK) {
                0 -> this
                2 -> "$this=="
                3 -> "$this="
                else -> this
            }

        internal fun halt(): Nothing {
            Runtime.getRuntime().halt(0)
            error("unreachable: halt() does not return")
        }

        private companion object {
            const val TAG = "AuthTestHarness"

            private val JSON = Json { ignoreUnknownKeys = true }

            /**
             * Shaped like a token so nothing upstream rejects it for being
             * malformed before it reaches the server, and labelled so it is
             * obvious in a capture that this was deliberate.
             */
            const val POISON = "nubecita-harness-deliberately-invalid-token"

            /**
             * Marks a fabricated process id in the stored marker. It does not
             * reach telemetry — see [stageDiedInFlightMarker].
             */
            const val HARNESS_PROCESS_ID_PREFIX = "harness-"

            /**
             * Back-dates the staged marker so it reads as a refresh from a
             * previous process rather than one racing this one.
             */
            const val MARKER_BACKDATE_MILLIS = 60_000L

            /**
             * Generous on purpose: this runs once, by hand, and a report lost to
             * an impatient halt costs a whole re-run plus a fresh sign-in.
             */
            const val TELEMETRY_PERSIST_DELAY_MILLIS = 3_000L

            const val MILLIS_PER_SECOND = 1_000L
            const val SECONDS_PER_MINUTE = 60L
            const val JWT_PART_COUNT = 3
            const val BASE64_BLOCK = 4
        }
    }
