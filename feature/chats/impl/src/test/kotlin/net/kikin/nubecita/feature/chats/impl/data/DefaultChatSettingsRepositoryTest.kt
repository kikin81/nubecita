package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.feature.chats.impl.AllowIncoming
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultChatSettingsRepository], mirroring
 * `DefaultProfileRepositoryUpdateProfileTest`: a real [XrpcClient] over a Ktor
 * [MockEngine] runs the SDK's `getRecord` / `putRecord` codepath against
 * deterministic responses. Assertions work on the recorded request bodies
 * (substring checks, not JSON parsing).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultChatSettingsRepositoryTest {
    private val testDid = "did:plc:abc"
    private val selfUri = "at://$testDid/chat.bsky.actor.declaration/self"

    @Test
    fun getAllowIncoming_readsRecordValue() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "bafcid", value = """{"allowIncoming":"all"}"""))
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.getAllowIncoming()

            assertEquals(AllowIncoming.Everyone, result.getOrNull())
        }

    @Test
    fun getAllowIncoming_recordNotFound_defaultsToFollowing() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() -> errorJson(HttpStatusCode.BadRequest, "RecordNotFound", "no record")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.getAllowIncoming()

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            assertEquals(AllowIncoming.Following, result.getOrNull())
        }

    @Test
    fun getAllowIncoming_unknownValue_defaultsToFollowing() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "bafcid", value = """{"allowIncoming":"sometimes"}"""))
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            assertEquals(AllowIncoming.Following, repo.getAllowIncoming().getOrNull())
        }

    @Test
    fun setAllowIncoming_writesWireValue_andSwapsOnCid() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(getRecordJson(cid = "bafcid", value = """{"allowIncoming":"all"}"""))
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.setAllowIncoming(AllowIncoming.NoOne)

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            assertEquals(2, engine.requestHistory.size)
            val body = putBody.await()
            assertTrue(body.contains("\"allowIncoming\":\"none\""), "wire value not written: $body")
            assertFalse(body.contains("\"all\""), "stale value leaked: $body")
            assertTrue(body.contains("\"swapRecord\":\"bafcid\""), "swapRecord(cid) missing: $body")
        }

    @Test
    fun setAllowIncoming_createsRecordWhenMissing_stampsType_noSwap() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() -> errorJson(HttpStatusCode.BadRequest, "RecordNotFound", "no record")
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.setAllowIncoming(AllowIncoming.Everyone)

            assertTrue(result.isSuccess, "expected success, got: ${result.exceptionOrNull()}")
            val body = putBody.await()
            assertTrue(body.contains("\"allowIncoming\":\"all\""), "wire value not written: $body")
            assertTrue(body.contains("chat.bsky.actor.declaration"), "\$type not stamped on create: $body")
            assertFalse(body.contains("swapRecord"), "create path must omit swapRecord: $body")
        }

    @Test
    fun setAllowIncoming_preservesUnknownKeys() =
        runTest {
            val putBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.isGetRecord() ->
                            okJson(
                                getRecordJson(
                                    cid = "bafcid",
                                    value = """{"allowIncoming":"all","someFutureKey":"keep"}""",
                                ),
                            )
                        request.isPutRecord() -> {
                            putBody.complete(request.body.toBodyString())
                            okJson(putRecordJson(cid = "bafnew"))
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            repo.setAllowIncoming(AllowIncoming.Following)

            val body = putBody.await()
            assertTrue(body.contains("\"someFutureKey\":\"keep\""), "unknown key dropped: $body")
            assertTrue(body.contains("\"allowIncoming\":\"following\""), "edit not applied: $body")
        }

    @Test
    fun signedOut_returnsFailure_withoutTouchingNetwork() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = false) { error("must not hit the network when signed out") }

            assertTrue(repo.getAllowIncoming().isFailure)
            assertTrue(repo.setAllowIncoming(AllowIncoming.NoOne).isFailure)
            assertEquals(0, engine.requestHistory.size)
        }

    // ---------- harness ----------

    private fun HttpRequestData.isGetRecord(): Boolean = url.encodedPath.endsWith("getRecord")

    private fun HttpRequestData.isPutRecord(): Boolean = url.encodedPath.endsWith("putRecord")

    private fun getRecordJson(
        cid: String,
        value: String,
    ): String = """{"uri":"$selfUri","cid":"$cid","value":$value}"""

    private fun putRecordJson(cid: String): String = """{"uri":"$selfUri","cid":"$cid"}"""

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.errorJson(
        status: HttpStatusCode,
        error: String,
        message: String,
    ): HttpResponseData =
        respond(
            ByteReadChannel("""{"error":"$error","message":"$message"}"""),
            status,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun newRepo(
        signedIn: Boolean,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultChatSettingsRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val repo =
            DefaultChatSettingsRepository(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        override suspend fun authenticated(): XrpcClient = xrpcClient
                    },
                sessionStateProvider =
                    object : SessionStateProvider {
                        override val state: StateFlow<SessionState> =
                            MutableStateFlow(
                                if (signedIn) {
                                    SessionState.SignedIn(handle = "alice.test", did = testDid)
                                } else {
                                    SessionState.SignedOut
                                },
                            )

                        override suspend fun refresh() = Unit
                    },
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to repo
    }
}

private fun OutgoingContent.toBodyString(): String =
    when (this) {
        is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
        is io.ktor.http.content.TextContent -> text
        else -> ""
    }
