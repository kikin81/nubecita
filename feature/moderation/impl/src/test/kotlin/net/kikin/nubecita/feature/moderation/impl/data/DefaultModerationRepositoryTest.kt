package net.kikin.nubecita.feature.moderation.impl.data

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.feature.moderation.impl.ReportReasons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultModerationRepositoryTest {
    private val postUri = "at://did:plc:author123/app.bsky.feed.post/3kxyz"
    private val postCid = "bafyreigh2akiscaildc"
    private val accountDid = "did:plc:target456"

    @Test
    fun `reportPost sends StrongRef subject with the correct uri and cid`() =
        runTest {
            val capture = RecordingEngine.respondingWith(emptyOkBody())
            val repo = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result =
                repo.reportPost(
                    uri = postUri,
                    cid = postCid,
                    reasonToken = ReportReasons.REASON_LEGACY_SPAM,
                    details = null,
                )

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.moderation.createReport", request.url.encodedPath)
            val body = jsonBody(capture.bodies.single())

            // reasonType is passed verbatim
            assertEquals(ReportReasons.REASON_LEGACY_SPAM, body["reasonType"]!!.jsonPrimitive.content)

            // subject is a com.atproto.repo.strongRef with the right uri/cid
            val subject = body["subject"]!!.jsonObject
            assertEquals("com.atproto.repo.strongRef", subject["\$type"]!!.jsonPrimitive.content)
            assertEquals(postUri, subject["uri"]!!.jsonPrimitive.content)
            assertEquals(postCid, subject["cid"]!!.jsonPrimitive.content)
        }

    @Test
    fun `reportAccount sends RepoRef subject with the correct did`() =
        runTest {
            val capture = RecordingEngine.respondingWith(emptyOkBody())
            val repo = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result =
                repo.reportAccount(
                    did = accountDid,
                    reasonToken = ReportReasons.REASON_HARASSMENT_TARGETED,
                    details = "context about the harassment",
                )

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val body = jsonBody(capture.bodies.single())

            assertEquals(
                ReportReasons.REASON_HARASSMENT_TARGETED,
                body["reasonType"]!!.jsonPrimitive.content,
            )

            val subject = body["subject"]!!.jsonObject
            assertEquals("com.atproto.admin.defs#repoRef", subject["\$type"]!!.jsonPrimitive.content)
            assertEquals(accountDid, subject["did"]!!.jsonPrimitive.content)
            // No uri/cid on a RepoRef
            assertNull(subject["uri"])
            assertNull(subject["cid"])

            // Details round-trip unchanged because they're well under 2000 graphemes
            assertEquals("context about the harassment", body["reason"]!!.jsonPrimitive.content)
        }

    @Test
    fun `modTool name is set to nubecita slash android on every request`() =
        runTest {
            val capture = RecordingEngine.respondingWith(emptyOkBody())
            val repo = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            repo.reportPost(postUri, postCid, ReportReasons.REASON_LEGACY_SPAM, details = null)
            repo.reportAccount(accountDid, ReportReasons.REASON_OTHER, details = "x")

            assertEquals(2, capture.requests.size)
            capture.bodies.forEach { rawBody ->
                val body = jsonBody(rawBody)
                val modTool = body["modTool"]!!.jsonObject
                assertEquals("nubecita/android", modTool["name"]!!.jsonPrimitive.content)
                // meta is intentionally absent in V1
                assertNull(modTool["meta"])
            }
        }

    @Test
    fun `null details omits the reason field from the wire payload`() =
        runTest {
            val capture = RecordingEngine.respondingWith(emptyOkBody())
            val repo = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            repo.reportPost(postUri, postCid, ReportReasons.REASON_LEGACY_SPAM, details = null)

            val body = jsonBody(capture.bodies.single())
            assertNull(body["reason"], "reason field should be absent when details is null")
        }

    @Test
    fun `blank details omits the reason field from the wire payload`() =
        runTest {
            val capture = RecordingEngine.respondingWith(emptyOkBody())
            val repo = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            repo.reportPost(postUri, postCid, ReportReasons.REASON_LEGACY_SPAM, details = "   ")

            val body = jsonBody(capture.bodies.single())
            assertNull(body["reason"], "reason field should be absent when details is blank")
        }

    @Test
    fun `details longer than 2000 graphemes is truncated before submission`() =
        runTest {
            val capture = RecordingEngine.respondingWith(emptyOkBody())
            val repo = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            // 3000 ASCII chars = 3000 graphemes (ASCII is 1:1 grapheme).
            val longDetails = "a".repeat(3000)
            repo.reportPost(postUri, postCid, ReportReasons.REASON_HARASSMENT_OTHER, details = longDetails)

            val body = jsonBody(capture.bodies.single())
            val sentReason = body["reason"]!!.jsonPrimitive.content
            assertEquals(2000, sentReason.length, "expected exactly 2000 chars, got ${sentReason.length}")
            assertFalse(sentReason == longDetails, "original 3000-char value should NOT be sent verbatim")
        }

    @Test
    fun `network IOException surfaces as Result_failure without being swallowed`() =
        runTest {
            val engine = MockEngine { throw IOException("simulated network failure") }
            val repo = newRepository(engine, UnconfinedTestDispatcher(testScheduler))

            val result =
                repo.reportPost(
                    postUri,
                    postCid,
                    ReportReasons.REASON_LEGACY_SPAM,
                    details = null,
                )

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertNotNull(cause)
            assertTrue(
                cause is IOException || cause?.cause is IOException,
                "expected IOException in cause chain, got $cause",
            )
        }

    @Test
    fun `5xx server response surfaces as Result_failure`() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            val repo = newRepository(engine, UnconfinedTestDispatcher(testScheduler))

            val result =
                repo.reportAccount(accountDid, ReportReasons.REASON_OTHER, details = "x")

            assertTrue(result.isFailure)
        }

    private fun newRepository(
        engine: MockEngine,
        dispatcher: kotlinx.coroutines.test.TestDispatcher,
    ): DefaultModerationRepository =
        DefaultModerationRepository(
            xrpcClientProvider = StaticXrpcClientProvider(authenticatedClient(engine)),
            dispatcher = dispatcher,
        )

    private fun authenticatedClient(engine: MockEngine): XrpcClient =
        XrpcClient(
            baseUrl = "https://pds.example.test",
            httpClient = HttpClient(engine),
        )

    private fun jsonBody(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject

    // createReport's response has 5 required fields. Return a minimal
    // valid response so the SDK's deserialization succeeds — the
    // values don't matter (the repository returns `Unit` on success);
    // shape does.
    private fun emptyOkBody(): String =
        """
        {
          "createdAt": "2026-05-19T00:00:00Z",
          "id": 1,
          "reasonType": "com.atproto.moderation.defs#reasonSpam",
          "reportedBy": "did:plc:reporter",
          "subject": {
            "${'$'}type": "com.atproto.admin.defs#repoRef",
            "did": "did:plc:reported"
          }
        }
        """.trimIndent()
}

/**
 * Captures every request the [MockEngine] receives — matches the shape
 * used by `DefaultLikeRepostRepositoryTest`, including pulling the
 * outgoing body's text inside the engine callback (where the
 * [io.ktor.http.content.OutgoingContent] is guaranteed-readable).
 */
private class RecordingEngine private constructor(
    val engine: MockEngine,
    val requests: List<HttpRequestData>,
    val bodies: List<String>,
) {
    companion object {
        fun respondingWith(body: String): RecordingEngine {
            val capturedRequests = mutableListOf<HttpRequestData>()
            val capturedBodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    capturedRequests += request
                    capturedBodies += (request.body as? TextContent)?.text.orEmpty()
                    respond(
                        content = ByteReadChannel(body),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            return RecordingEngine(
                engine = engine,
                requests = capturedRequests,
                bodies = capturedBodies,
            )
        }
    }
}

private class StaticXrpcClientProvider(
    private val client: XrpcClient,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = client
}
