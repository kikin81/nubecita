package net.kikin.nubecita.core.profile

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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The shared profile-record read-modify-write (nubecita-iwjz.2): every field the
 * transform doesn't touch is written back unchanged, with swapRecord set to the
 * read CID; a missing record is created with `$type` and no swapRecord.
 */
class DefaultProfileRecordWriterTest {
    private val did = "did:plc:alice"
    private val selfUri = "at://$did/app.bsky.actor.profile/self"

    private val fullRecord =
        """{"${'$'}type":"app.bsky.actor.profile","displayName":"Alice","description":"old",""" +
            """"avatar":{"${'$'}type":"blob","ref":{"${'$'}link":"bafavatar"},"mimeType":"image/jpeg","size":1},""" +
            """"banner":{"${'$'}type":"blob","ref":{"${'$'}link":"bafbanner"},"mimeType":"image/jpeg","size":2},""" +
            """"labels":{"${'$'}type":"com.atproto.label.defs#selfLabels","values":[{"val":"!no-unauthenticated"}]},""" +
            """"links":[{"uri":"at://$did/app.bsky.actor.link/1","cid":"baflink"}],""" +
            """"pinnedPost":{"uri":"at://$did/app.bsky.feed.post/p","cid":"bafp"},"futureKey":42}"""

    @Test
    fun updatePreservesUntouchedFields() =
        runTest {
            val puts = mutableListOf<JsonObject>()
            val writer = writer(puts) { okJson(getRecordJson("bafcid", fullRecord)) }

            val result = writer.update { existing -> JsonObject(requireNotNull(existing) + ("description" to JsonPrimitive("new"))) }

            assertEquals(Result.success(true), result)
            val written = puts.single().getValue("record").jsonObject
            val expected =
                Json
                    .parseToJsonElement(fullRecord)
                    .jsonObject
                    .toMutableMap()
                    .apply { put("description", JsonPrimitive("new")) }
            assertEquals(JsonObject(expected), written)
        }

    @Test
    fun updateSendsSwapRecordCid() =
        runTest {
            val puts = mutableListOf<JsonObject>()
            val writer = writer(puts) { okJson(getRecordJson("bafcid", fullRecord)) }

            writer.update { it }

            assertEquals(
                "bafcid",
                puts
                    .single()
                    .getValue("swapRecord")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun updateCreatesRecordWhenNoneExists() =
        runTest {
            val puts = mutableListOf<JsonObject>()
            var received: JsonObject? = JsonObject(emptyMap())
            val writer =
                writer(puts) {
                    respond(
                        ByteReadChannel("""{"error":"RecordNotFound","message":"Could not locate record"}"""),
                        HttpStatusCode.BadRequest,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }

            val result =
                writer.update { existing ->
                    received = existing
                    JsonObject(mapOf("pinnedPost" to JsonPrimitive("x")))
                }

            assertEquals(Result.success(true), result)
            assertEquals(null, received)
            val body = puts.single()
            assertFalse(body.containsKey("swapRecord"), "create must omit swapRecord: $body")
            assertEquals(
                "app.bsky.actor.profile",
                body
                    .getValue("record")
                    .jsonObject
                    .getValue("\$type")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun nullTransformSkipsWrite() =
        runTest {
            val puts = mutableListOf<JsonObject>()
            val writer = writer(puts) { okJson(getRecordJson("bafcid", fullRecord)) }

            val result = writer.update { null }

            assertEquals(Result.success(false), result)
            assertTrue(puts.isEmpty())
        }

    @Test
    fun signedOutFails() =
        runTest {
            val writer = writer(mutableListOf(), signedIn = false) { okJson(getRecordJson("bafcid", fullRecord)) }

            val result = writer.update { it }

            assertTrue(result.exceptionOrNull() is NoSessionException)
        }

    private fun getRecordJson(
        cid: String,
        value: String,
    ): String = """{"uri":"$selfUri","cid":"$cid","value":$value}"""

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData = respond(ByteReadChannel(json), HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))

    private fun writer(
        puts: MutableList<JsonObject>,
        signedIn: Boolean = true,
        onGetRecord: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DefaultProfileRecordWriter {
        val engine =
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("putRecord")) {
                    puts += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    okJson("""{"uri":"$selfUri","cid":"bafnew"}""")
                } else {
                    onGetRecord(request)
                }
            }
        val client = XrpcClient(baseUrl = "https://test.invalid", httpClient = HttpClient(engine), authProvider = NoAuth)
        return DefaultProfileRecordWriter(
            xrpcClientProvider =
                object : XrpcClientProvider {
                    override suspend fun authenticated(): XrpcClient = client
                },
            sessionStateProvider =
                object : SessionStateProvider {
                    override val state: StateFlow<SessionState> =
                        MutableStateFlow(if (signedIn) SessionState.SignedIn(handle = "alice.test", did = did) else SessionState.SignedOut)

                    override suspend fun refresh() = Unit
                },
            dispatcher = UnconfinedTestDispatcher(),
        )
    }
}
