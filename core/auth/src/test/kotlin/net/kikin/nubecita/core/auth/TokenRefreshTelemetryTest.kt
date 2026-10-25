package net.kikin.nubecita.core.auth

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import net.kikin.nubecita.core.logging.CrashReporter
import org.junit.jupiter.api.Assertions.assertEquals
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

    private fun telemetry(scope: TestScope) =
        TokenRefreshTelemetry(
            crashReporter = crashReporter,
            analytics = analytics,
            history = history,
            foreground = FixedForegroundSignal(true),
            scope = scope.backgroundScope,
            clock = clock,
        )

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
                    "last_ok_refresh_mins_ago" to "none",
                    "last_ok_refresh_caller" to "none",
                    "last_lost_refresh_mins_ago" to "none",
                    "last_lost_refresh_caller" to "none",
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
