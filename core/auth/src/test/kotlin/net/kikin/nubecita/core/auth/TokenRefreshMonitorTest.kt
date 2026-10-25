package net.kikin.nubecita.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.TokenRefresh
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import net.kikin.nubecita.core.logging.CrashReporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException
import kotlin.time.Clock
import kotlin.time.Instant

class TokenRefreshMonitorTest {
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val analytics = mockk<AnalyticsClient>(relaxed = true)

    private fun telemetry(scope: TestScope) =
        TokenRefreshTelemetry(
            crashReporter = crashReporter,
            analytics = analytics,
            history = InMemoryRefreshHistoryStore(),
            foreground = FixedForegroundSignal(false),
            scope = scope.backgroundScope,
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(1_720_000_000_000L)
                },
        )

    private fun client(
        telemetry: TokenRefreshTelemetry,
        engine: MockEngine,
    ) = HttpClient(engine) { install(tokenRefreshMonitor(telemetry)) }

    private suspend fun HttpClient.refresh(grantType: String = "refresh_token") =
        submitForm(
            url = "https://bsky.social/oauth/token",
            formParameters =
                Parameters.build {
                    append("grant_type", grantType)
                    append("refresh_token", "rt")
                },
        )

    private fun expect(
        caller: TokenRefreshCaller,
        outcome: TokenRefreshOutcome,
    ) = verify { analytics.log(TokenRefresh(caller = caller, outcome = outcome, foreground = false)) }

    @Test
    fun `refresh under a RefreshCaller tag is attributed to that caller`() =
        runTest {
            val http = client(telemetry(this), MockEngine { respond("""{"access_token":"a"}""", HttpStatusCode.OK) })

            withContext(RefreshCaller.DmPoll) { http.refresh() }

            expect(TokenRefreshCaller.DmPoll, TokenRefreshOutcome.Ok)
        }

    @Test
    fun `untagged refresh is reported as untagged`() =
        runTest {
            val http = client(telemetry(this), MockEngine { respond("{}", HttpStatusCode.OK) })

            http.refresh()

            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.Ok)
        }

    @Test
    fun `non-refresh requests are not observed`() =
        runTest {
            val http = client(telemetry(this), MockEngine { respond("{}", HttpStatusCode.OK) })

            http.get("https://bsky.social/xrpc/app.bsky.feed.getTimeline")
            http.refresh(grantType = "authorization_code")

            verify(exactly = 0) { analytics.log(any()) }
        }

    @Test
    fun `nonce challenge is classified and the body is still readable by the SDK`() =
        runTest {
            val body = """{"error":"use_dpop_nonce","error_description":"Authorization server requires nonce"}"""
            val http = client(telemetry(this), MockEngine { respond(body, HttpStatusCode.BadRequest) })

            val response = http.refresh()

            assertEquals(body, response.bodyAsText())
            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.NonceChallenge)
        }

    @Test
    fun `invalid_grant is classified and its description kept for the clear`() =
        runTest {
            val telemetry = telemetry(this)
            val body = """{"error":"invalid_grant","error_description":"Refresh token replayed"}"""
            val http = client(telemetry, MockEngine { respond(body, HttpStatusCode.BadRequest) })

            withContext(RefreshCaller.Widget) { http.refresh() }

            expect(TokenRefreshCaller.Widget, TokenRefreshOutcome.InvalidGrant)
            val keys = telemetry.clearKeys()
            assertEquals("widget", keys["clearing_refresh_caller"])
            assertEquals("Refresh token replayed", keys["clearing_refresh_error"])
        }

    @Test
    fun `a post-send failure is a lost response, recorded as a non-fatal, and rethrown`() =
        runTest {
            val http = client(telemetry(this), MockEngine { throw IOException("Socket timeout has expired") })

            val thrown = runCatching { withContext(RefreshCaller.DmPoll) { http.refresh() } }.exceptionOrNull()

            assertTrue(thrown is IOException, "expected the IOException to propagate, got $thrown")
            expect(TokenRefreshCaller.DmPoll, TokenRefreshOutcome.LostResponse)
            verify { crashReporter.setCustomKey("lost_refresh_caller", "dm_poll") }
            verify { crashReporter.recordException(match { it is TokenRefreshOutcomeUnknownException }) }
        }

    @Test
    fun `a pre-send failure is not_sent and records no non-fatal`() =
        runTest {
            val http = client(telemetry(this), MockEngine { throw UnknownHostException("bsky.social") })

            runCatching { http.refresh() }

            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.NotSent)
            verify(exactly = 0) { crashReporter.recordException(any()) }
        }
}

internal class InMemoryRefreshHistoryStore : RefreshHistoryStore {
    var history = RefreshHistory(lastOk = null, lastLostResponse = null)

    override suspend fun recordOk(record: RefreshRecord) {
        history = history.copy(lastOk = record)
    }

    override suspend fun recordLostResponse(record: RefreshRecord) {
        history = history.copy(lastLostResponse = record)
    }

    override suspend fun history(): RefreshHistory = history
}

internal class FixedForegroundSignal(
    private val value: Boolean,
) : ProcessForegroundSignal {
    override fun isForegrounded(): Boolean = value
}
