package net.kikin.nubecita.core.videoupload.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.videoupload.VideoUploadError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UploadLimitsProbeTest {
    private val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()

    private fun probe(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
        token: suspend () -> String = { "service-auth-jwt" },
    ): UploadLimitsProbe {
        val engine =
            MockEngine { request ->
                requests += request
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val factory =
            VideoServiceClientFactory(
                httpClient = HttpClient(engine),
                serviceAuthProvider =
                    object : ServiceAuthProvider {
                        override suspend fun videoServiceToken(lxm: String): String = token()

                        override suspend fun blobUploadToken(): String = token()
                    },
            )
        return UploadLimitsProbe(factory)
    }

    @Test
    fun `a permitted account proceeds`() =
        runTest {
            val verdict =
                probe(body = """{"canUpload":true,"remainingDailyVideos":10,"remainingDailyBytes":1000}""").check()

            assertEquals(UploadLimitsVerdict.Permitted, verdict)
        }

    /**
     * The server's own text is passed through verbatim. The two real causes are
     * an unverified account email and an exhausted daily quota; Bluesky phrases
     * those better than a local guess, and will phrase future ones we do not
     * know about at all.
     */
    @Test
    fun `a refusal surfaces the server message verbatim`() =
        runTest {
            val verdict =
                probe(
                    body = """{"canUpload":false,"message":"Account email must be verified to upload video."}""",
                ).check()

            val error = (verdict as UploadLimitsVerdict.Rejected).error
            assertEquals(
                VideoUploadError.NotPermitted("Account email must be verified to upload video."),
                error,
            )
        }

    @Test
    fun `a refusal with only an error field still carries a reason`() =
        runTest {
            val verdict = probe(body = """{"canUpload":false,"error":"DailyLimitExceeded"}""").check()

            assertEquals(
                VideoUploadError.NotPermitted("DailyLimitExceeded"),
                (verdict as UploadLimitsVerdict.Rejected).error,
            )
        }

    /**
     * A transport failure must be distinguishable from a quota refusal — only
     * one of them is worth a retry button.
     */
    @Test
    fun `a transport failure maps to Network, not NotPermitted`() =
        runTest {
            val verdict = probe(status = HttpStatusCode.ServiceUnavailable, body = "upstream down").check()

            val error = (verdict as UploadLimitsVerdict.Rejected).error
            assertTrue(error is VideoUploadError.Network, "expected Network, was ${error::class.simpleName}")
        }

    /**
     * `getUploadLimits` is documented "for the authenticated user". The token
     * reaches it through the XrpcClient's AuthProvider, not a transport plugin,
     * so this asserts the wiring the design depends on.
     */
    @Test
    fun `the request carries the service-auth bearer and targets the video service`() =
        runTest {
            probe(body = """{"canUpload":true}""").check()

            val request = requests.single()
            assertEquals("video.bsky.app", request.url.host)
            assertEquals("Bearer service-auth-jwt", request.headers[HttpHeaders.Authorization])
        }

    /**
     * The token is resolved per request rather than captured at construction —
     * a service-auth JWT expires in 30 minutes and is minted lazily, so binding
     * it up front would pin one that may not exist yet.
     */
    @Test
    fun `the token is resolved lazily at request time`() =
        runTest {
            var minted = 0
            probe(body = """{"canUpload":true}""", token = {
                minted++
                "jwt-$minted"
            }).check()

            assertEquals(1, minted, "token must be fetched when the request is made, not before")
            assertEquals("Bearer jwt-1", requests.single().headers[HttpHeaders.Authorization])
        }
}
