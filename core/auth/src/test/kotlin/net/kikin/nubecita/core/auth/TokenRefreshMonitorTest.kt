package net.kikin.nubecita.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.ClientAuthRegime
import net.kikin.nubecita.core.analytics.IssuerKind
import net.kikin.nubecita.core.analytics.RefreshErrorDetail
import net.kikin.nubecita.core.analytics.TokenRefresh
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import net.kikin.nubecita.core.analytics.TokenRequestPath
import net.kikin.nubecita.core.logging.CrashReporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.time.Clock
import kotlin.time.Instant

class TokenRefreshMonitorTest {
    private val crashReporter = mockk<CrashReporter>(relaxed = true)
    private val analytics = mockk<AnalyticsClient>(relaxed = true)

    private fun telemetry(
        scope: TestScope,
        history: InMemoryRefreshHistoryStore = InMemoryRefreshHistoryStore(),
    ) = TokenRefreshTelemetry(
        crashReporter = crashReporter,
        analytics = analytics,
        history = history,
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
        path: TokenRequestPath = TokenRequestPath.Direct,
        clientAuth: ClientAuthRegime = ClientAuthRegime.Unknown,
        replayed: Boolean = false,
        issuer: IssuerKind = IssuerKind.BskySocial,
        errorDetail: RefreshErrorDetail? = null,
    ) = verify {
        analytics.log(
            TokenRefresh(
                caller = caller,
                outcome = outcome,
                foreground = false,
                path = path,
                clientAuth = clientAuth,
                replayed = replayed,
                issuer = issuer,
                errorDetail = errorDetail,
            ),
        )
    }

    /** A refresh sent the way the mediated transport sends it: to the backend, tagged with its route. */
    private suspend fun HttpClient.mediatedRefresh(issuer: String = "https://bsky.social") =
        withContext(TokenPathElement(TokenRequestPath.Mediated)) {
            submitForm(
                url = "https://auth.nubecita.app/token",
                formParameters =
                    Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", "rt")
                    },
            ) { header(MediatedTokenEndpointTransport.HEADER_ISSUER, issuer) }
        }

    private fun relayedHeaders(vararg extra: Pair<String, String>) =
        headersOf(
            *(
                listOf(
                    MediatedTokenEndpointTransport.HEADER_ORIGIN to MediatedTokenEndpointTransport.ORIGIN_UPSTREAM,
                    MediatedTokenEndpointTransport.HEADER_AUTH_METHOD to "private_key_jwt",
                ) + extra
            ).map { (name, value) -> name to listOf(value) }.toTypedArray(),
        )

    @Test
    fun `a mediated refresh records its route, the reported client registration and the issuer it named`() =
        runTest {
            val http =
                client(telemetry(this), MockEngine { respond("""{"access_token":"a"}""", HttpStatusCode.OK, relayedHeaders()) })

            http.mediatedRefresh()

            expect(
                TokenRefreshCaller.Untagged,
                TokenRefreshOutcome.Ok,
                path = TokenRequestPath.Mediated,
                clientAuth = ClientAuthRegime.Confidential,
                issuer = IssuerKind.BskySocial,
            )
        }

    @Test
    fun `a self-hosted issuer is recorded only as other, never by name`() =
        runTest {
            val http =
                client(telemetry(this), MockEngine { respond("""{"access_token":"a"}""", HttpStatusCode.OK, relayedHeaders()) })

            http.mediatedRefresh(issuer = "https://pds.alice.example")

            expect(
                TokenRefreshCaller.Untagged,
                TokenRefreshOutcome.Ok,
                path = TokenRequestPath.Mediated,
                clientAuth = ClientAuthRegime.Confidential,
                issuer = IssuerKind.Other,
            )
            verify(exactly = 0) { crashReporter.log(match { it.contains("alice") }) }
        }

    @Test
    fun `a stored response served by the backend is recorded as replayed`() =
        runTest {
            val http =
                client(
                    telemetry(this),
                    MockEngine {
                        respond(
                            """{"access_token":"a"}""",
                            HttpStatusCode.OK,
                            relayedHeaders(MediatedTokenEndpointTransport.HEADER_REPLAYED to "1"),
                        )
                    },
                )

            http.mediatedRefresh()

            expect(
                TokenRefreshCaller.Untagged,
                TokenRefreshOutcome.Ok,
                path = TokenRequestPath.Mediated,
                clientAuth = ClientAuthRegime.Confidential,
                replayed = true,
            )
        }

    @Test
    fun `the backend's own answer is a mediator error, even when its body says invalid_grant`() =
        runTest {
            val t = telemetry(this)
            val http = client(t, MockEngine { respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) })

            http.mediatedRefresh()

            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.MediatorError, path = TokenRequestPath.Mediated)
            // Not an invalid_grant, so it can never be read as what cleared a session.
            assertEquals("none", t.clearKeys()["clearing_refresh_caller"])
        }

    @Test
    fun `an unmarked 200 through the backend is not counted as a rotation`() =
        runTest {
            val http = client(telemetry(this), MockEngine { respond("""{"access_token":"a"}""", HttpStatusCode.OK) })

            http.mediatedRefresh()

            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.MediatorError, path = TokenRequestPath.Mediated)
        }

    @Test
    fun `a relayed invalid_grant is still invalid_grant, with its route and registration on the clear`() =
        runTest {
            val t = telemetry(this)
            val http =
                client(
                    t,
                    MockEngine {
                        respond(
                            """{"error":"invalid_grant","error_description":"Session expired"}""",
                            HttpStatusCode.BadRequest,
                            relayedHeaders(),
                        )
                    },
                )

            http.mediatedRefresh()

            expect(
                TokenRefreshCaller.Untagged,
                TokenRefreshOutcome.InvalidGrant,
                path = TokenRequestPath.Mediated,
                clientAuth = ClientAuthRegime.Confidential,
                errorDetail = RefreshErrorDetail.SessionExpired,
            )
            val keys = t.clearKeys()
            assertEquals("mediated", keys["clearing_refresh_path"])
            assertEquals("confidential", keys["clearing_client_auth"])
        }

    @Test
    fun `a refresh sent direct after the backend was unreachable is recorded as a fallback`() =
        runTest {
            val http = client(telemetry(this), MockEngine { respond("""{"access_token":"a"}""", HttpStatusCode.OK) })

            withContext(TokenPathElement(TokenRequestPath.DirectFallback)) { http.refresh() }

            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.Ok, path = TokenRequestPath.DirectFallback)
        }

    @Test
    fun `the backend's reason is kept, and nothing else from its body`() {
        assertEquals("mediator_bypass", classifyMediatorAnswer("""{"error":"mediator_bypass","forwarded":false}""").description)
        assertEquals(null, classifyMediatorAnswer("""{"error":"did:plc:alice says no"}""").description)
        assertEquals(null, classifyMediatorAnswer("<html>gateway</html>").description)
        assertEquals(TokenRefreshOutcome.MediatorError, classifyMediatorAnswer(null).outcome)
    }

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

            expect(TokenRefreshCaller.Widget, TokenRefreshOutcome.InvalidGrant, errorDetail = RefreshErrorDetail.RefreshTokenReplayed)
            val keys = telemetry.clearKeys()
            assertEquals("widget", keys["clearing_refresh_caller"])
            assertEquals("Refresh token replayed", keys["clearing_refresh_error"])
            assertEquals("refresh_token_replayed", keys["clearing_refresh_detail"])
        }

    @Test
    fun `a refresh rejected because the server had already seen its proof says so`() =
        runTest {
            // What the app sees when a refresh reached the server twice: the
            // first copy spent the token and its answer was lost; this is the
            // answer to the second.
            val body = """{"error":"invalid_dpop_proof","error_description":"DPoP proof replayed"}"""
            val http = client(telemetry(this), MockEngine { respond(body, HttpStatusCode.Unauthorized) })

            withContext(RefreshCaller.DmPoll) { http.refresh() }

            expect(TokenRefreshCaller.DmPoll, TokenRefreshOutcome.Rejected, errorDetail = RefreshErrorDetail.DpopProofReplayed)
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

    /** Before the POST, or a process that dies mid-request leaves nothing to find. */
    @Test
    fun `a refresh is marked in flight before it is sent`() =
        runTest {
            val history = InMemoryRefreshHistoryStore()
            var markedWhenSent: InFlightRefresh? = null
            val http =
                client(
                    telemetry(this, history),
                    MockEngine {
                        markedWhenSent = history.inFlight
                        respond("{}", HttpStatusCode.OK)
                    },
                )

            withContext(RefreshCaller.Widget) { http.refresh() }

            assertEquals(TokenRefreshCaller.Widget, markedWhenSent?.caller)
        }

    /**
     * OkHttp re-sends a request whose connection dropped mid-flight, keeping the
     * first failure as suppressed. If the retry then fails DNS, the first POST
     * may still have reached the server and consumed the token.
     */
    @Test
    fun `a pre-send failure that hides an earlier aborted attempt is a lost response`() =
        runTest {
            val retried =
                UnknownHostException("bsky.social").apply {
                    addSuppressed(SocketException("Software caused connection abort"))
                }
            val http = client(telemetry(this), MockEngine { throw retried })

            runCatching { http.refresh() }

            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.LostResponse)
        }

    @Test
    fun `a pre-send failure whose suppressed attempts also failed before sending is still not_sent`() =
        runTest {
            val retried =
                UnknownHostException("bsky.social").apply {
                    addSuppressed(ConnectException("Connection refused"))
                }
            val http = client(telemetry(this), MockEngine { throw retried })

            runCatching { http.refresh() }

            expect(TokenRefreshCaller.Untagged, TokenRefreshOutcome.NotSent)
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
    var inFlight: InFlightRefresh? = null
    var completedAt: Long? = null
    var failDiedInFlightWrite = false

    override suspend fun recordOk(record: RefreshRecord) {
        history = history.copy(lastOk = record)
    }

    override suspend fun recordLostResponse(record: RefreshRecord) {
        history = history.copy(lastLostResponse = record)
    }

    override suspend fun recordDiedInFlight(record: RefreshRecord) {
        if (failDiedInFlightWrite) throw java.io.IOException("disk full")
        history = history.copy(lastDiedInFlight = record)
    }

    override suspend fun markInFlight(refresh: InFlightRefresh) {
        inFlight = refresh
    }

    override suspend fun recordCompleted(epochMillis: Long) {
        completedAt = maxOf(completedAt ?: 0L, epochMillis)
    }

    override suspend fun unfinished(): InFlightRefresh? = inFlight?.takeIf { marker -> (completedAt ?: Long.MIN_VALUE) < marker.epochMillis }

    override suspend fun clearInFlight() {
        inFlight = null
    }

    override suspend fun history(): RefreshHistory = history
}

internal class FixedForegroundSignal(
    private val value: Boolean,
) : ProcessForegroundSignal {
    override fun isForegrounded(): Boolean = value
}
