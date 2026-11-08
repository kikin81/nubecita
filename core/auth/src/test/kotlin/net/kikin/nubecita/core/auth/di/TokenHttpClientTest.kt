package net.kikin.nubecita.core.auth.di

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import net.kikin.nubecita.core.auth.TokenRefreshTelemetry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TokenHttpClientTest {
    @Test
    fun `the token endpoint's OkHttp client never re-sends a request`() {
        // OkHttp re-sends a POST whose connection dropped mid-flight. A
        // re-sent refresh is a replay, and the server answers a replay by
        // deleting the session.
        assertFalse(NetworkEngineModule.tokenEndpointOkHttpClient().retryOnConnectionFailure)
    }

    @Test
    fun `the token client still reports refresh POSTs to telemetry`() =
        runTest {
            val telemetry = mockk<TokenRefreshTelemetry>(relaxed = true)
            val engine =
                MockEngine {
                    respond(
                        """{"access_token":"at","token_type":"DPoP"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = HttpClientModule.provideTokenHttpClient(engine, telemetry)

            client.submitForm(
                url = "https://bsky.social/oauth/token",
                formParameters =
                    Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", "rt")
                    },
            )

            verify { telemetry.onAttempt(any(), TokenRefreshOutcome.Ok, any(), any(), any()) }
        }
}
