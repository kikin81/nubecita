package net.kikin.nubecita.core.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.RefreshErrorDetail
import net.kikin.nubecita.core.analytics.TokenRefresh
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import net.kikin.nubecita.core.logging.CrashReporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

class TokenRefreshTelemetryTest {
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val analytics = mockk<AnalyticsClient>(relaxed = true)
    private val history = InMemoryRefreshHistoryStore()

    private var nowMillis = 1_720_000_000_000L
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
        }

    private fun telemetry(
        scope: TestScope,
        processId: String = "this-process",
    ) = TokenRefreshTelemetry(
        crashReporter = crashReporter,
        analytics = analytics,
        history = history,
        foreground = FixedForegroundSignal(true),
        scope = scope.backgroundScope,
        clock = clock,
        processId = processId,
    )

    @Test
    fun `a refresh the previous process never finished is reported once`() =
        runTest {
            history.inFlight =
                InFlightRefresh(nowMillis, TokenRefreshCaller.DmPoll, foreground = false, processId = "earlier-process")
            val telemetry = telemetry(this)

            telemetry.reportRefreshDiedInFlight()
            telemetry.reportRefreshDiedInFlight()

            verify(exactly = 1) { crashReporter.recordException(match { it is TokenRefreshDiedInFlightException }) }
            verify(exactly = 1) {
                analytics.log(
                    TokenRefresh(TokenRefreshCaller.DmPoll, TokenRefreshOutcome.ProcessDied, foreground = false),
                )
            }
        }

    @Test
    fun `a failed history write does not report the same death again`() =
        runTest {
            history.inFlight =
                InFlightRefresh(nowMillis, TokenRefreshCaller.DmPoll, foreground = false, processId = "earlier-process")
            history.failDiedInFlightWrite = true
            val telemetry = telemetry(this)

            telemetry.reportRefreshDiedInFlight()
            telemetry.reportRefreshDiedInFlight()

            verify(exactly = 1) { crashReporter.recordException(match { it is TokenRefreshDiedInFlightException }) }
        }

    @Test
    fun `a refresh that finished leaves nothing to report`() =
        runTest {
            val earlier = telemetry(this, processId = "earlier-process")
            earlier.onSending(TokenRefreshCaller.Untagged)
            nowMillis += 300
            earlier.onAttempt(TokenRefreshCaller.Untagged, TokenRefreshOutcome.Ok, durationMillis = 300)
            runCurrent()

            telemetry(this).reportRefreshDiedInFlight()

            verify(exactly = 0) { crashReporter.recordException(any()) }
        }

    /** The recovery pass can run while this process is itself mid-refresh. */
    @Test
    fun `this process's own refresh in progress is not reported`() =
        runTest {
            val telemetry = telemetry(this)
            telemetry.onSending(TokenRefreshCaller.Untagged)

            telemetry.reportRefreshDiedInFlight()

            verify(exactly = 0) { crashReporter.recordException(any()) }
        }

    @Test
    fun `the clear names the last refresh that died in flight`() =
        runTest {
            history.inFlight =
                InFlightRefresh(nowMillis, TokenRefreshCaller.Widget, foreground = false, processId = "earlier-process")
            val telemetry = telemetry(this)
            telemetry.reportRefreshDiedInFlight()
            nowMillis += 20 * MINUTE

            val keys = telemetry.clearKeys()

            assertEquals("20", keys["last_died_refresh_mins_ago"])
            assertEquals("widget", keys["last_died_refresh_caller"])
        }

    @Test
    fun `ok and lost-response refreshes persist and surface as minutes ago on the clear`() =
        runTest {
            val telemetry = telemetry(this)

            telemetry.onAttempt(TokenRefreshCaller.Untagged, TokenRefreshOutcome.Ok, durationMillis = 120)
            nowMillis += 30 * MINUTE
            telemetry.onAttempt(
                TokenRefreshCaller.DmPoll,
                TokenRefreshOutcome.LostResponse,
                durationMillis = 30_000,
                cause = IOException("timeout"),
            )
            runCurrent()
            nowMillis += 90 * MINUTE

            val keys = telemetry.clearKeys()

            assertEquals("120", keys["last_ok_refresh_mins_ago"])
            assertEquals("untagged", keys["last_ok_refresh_caller"])
            assertEquals("90", keys["last_lost_refresh_mins_ago"])
            assertEquals("dm_poll", keys["last_lost_refresh_caller"])
            // The last attempt was not an invalid_grant, so nothing is named as the trigger.
            assertEquals("none", keys["clearing_refresh_caller"])
        }

    @Test
    fun `an invalid_grant trigger is reported once and never leaks into a later clear`() =
        runTest {
            val telemetry = telemetry(this)
            telemetry.onAttempt(
                TokenRefreshCaller.Widget,
                TokenRefreshOutcome.InvalidGrant,
                durationMillis = 200,
                errorDescription = "Refresh token replayed",
            )

            assertEquals("widget", telemetry.clearKeys()["clearing_refresh_caller"])
            assertEquals("none", telemetry.clearKeys()["clearing_refresh_caller"])
        }

    @Test
    fun `a rejected refresh records its error code and why, never the server's text`() =
        runTest {
            val logged = mutableListOf<String>()
            every { crashReporter.log(capture(logged)) } returns Unit
            val telemetry = telemetry(this)

            telemetry.onAttempt(
                TokenRefreshCaller.DmPoll,
                TokenRefreshOutcome.Rejected,
                durationMillis = 593,
                errorCode = "invalid_dpop_proof",
                errorDescription = "DPoP proof replayed",
            )

            val line = logged.single()
            assertTrue(line.contains("outcome=rejected"), line)
            assertTrue(line.endsWith("error=invalid_dpop_proof detail=dpop_proof_replayed"), line)
            assertFalse(line.contains("DPoP proof replayed"), line)
            verify {
                analytics.log(
                    TokenRefresh(
                        TokenRefreshCaller.DmPoll,
                        TokenRefreshOutcome.Rejected,
                        foreground = true,
                        errorDetail = RefreshErrorDetail.DpopProofReplayed,
                    ),
                )
            }
        }

    @Test
    fun `a refresh the server accepted has no error detail`() =
        runTest {
            val logged = mutableListOf<String>()
            every { crashReporter.log(capture(logged)) } returns Unit
            val telemetry = telemetry(this)

            telemetry.onAttempt(TokenRefreshCaller.DmPoll, TokenRefreshOutcome.Ok, durationMillis = 300)

            assertFalse(logged.single().contains("detail="), logged.single())
            verify { analytics.log(TokenRefresh(TokenRefreshCaller.DmPoll, TokenRefreshOutcome.Ok, foreground = true)) }
        }

    @Test
    fun `the clear names why the server rejected the refresh that caused it`() =
        runTest {
            val telemetry = telemetry(this)
            telemetry.onAttempt(
                TokenRefreshCaller.Widget,
                TokenRefreshOutcome.InvalidGrant,
                durationMillis = 200,
                errorCode = "invalid_grant",
                errorDescription = "Session expired",
            )

            assertEquals("session_expired", telemetry.clearKeys()["clearing_refresh_detail"])
            assertEquals("none", telemetry.clearKeys()["clearing_refresh_detail"])
        }

    @Test
    fun `a stale invalid_grant is not named as the trigger`() =
        runTest {
            val telemetry = telemetry(this)
            telemetry.onAttempt(TokenRefreshCaller.Widget, TokenRefreshOutcome.InvalidGrant, durationMillis = 200)
            nowMillis += 10 * MINUTE

            assertEquals("none", telemetry.clearKeys()["clearing_refresh_caller"])
        }

    @Test
    fun `with no history every key reads none`() =
        runTest {
            val keys = telemetry(this).clearKeys()

            assertEquals(
                mapOf(
                    "clearing_refresh_caller" to "none",
                    "clearing_refresh_fg" to "none",
                    "clearing_refresh_error" to "none",
                    "clearing_refresh_path" to "none",
                    "clearing_client_auth" to "none",
                    "clearing_refresh_detail" to "none",
                    "last_ok_refresh_mins_ago" to "none",
                    "last_ok_refresh_caller" to "none",
                    "last_lost_refresh_mins_ago" to "none",
                    "last_lost_refresh_caller" to "none",
                    "last_died_refresh_mins_ago" to "none",
                    "last_died_refresh_caller" to "none",
                ),
                keys,
            )
        }

    @Test
    fun `a throwing analytics backend never propagates into the refresh path`() =
        runTest {
            every { analytics.log(any()) } throws IllegalStateException("analytics backend down")

            telemetry(this).onAttempt(TokenRefreshCaller.Widget, TokenRefreshOutcome.Ok, durationMillis = 1)
        }

    private companion object {
        const val MINUTE = 60_000L
    }
}
