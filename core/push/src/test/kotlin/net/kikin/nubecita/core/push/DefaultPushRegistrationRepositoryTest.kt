package net.kikin.nubecita.core.push

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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultPushRegistrationRepositoryTest {
    private val viewerDid = "did:plc:viewer123"
    private val fcmToken = "fcm-token-xyz"
    private val appId = "net.kikin.nubecita"

    @Test
    fun `register posts to registerPush with the gateway proxy header and android platform body`() =
        runTest {
            val capture = RecordingEngine.respondingWithEmpty()
            val repository = repositoryWith(capture.engine)

            val result = repository.register(did = viewerDid, fcmToken = fcmToken)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")

            val request = capture.requests.single()
            assertEquals("/xrpc/app.bsky.notification.registerPush", request.url.encodedPath)
            assertEquals(
                "did:web:push.nubecita.app#bsky_notif",
                request.headers["atproto-proxy"],
            )
            val body = capture.bodies.single().asJsonObject()
            assertEquals("did:web:push.nubecita.app", body["serviceDid"]!!.jsonPrimitive.content)
            assertEquals(fcmToken, body["token"]!!.jsonPrimitive.content)
            assertEquals("android", body["platform"]!!.jsonPrimitive.content)
            assertEquals(appId, body["appId"]!!.jsonPrimitive.content)
        }

    @Test
    fun `unregister posts to unregisterPush with the gateway proxy header and identical body shape`() =
        runTest {
            val capture = RecordingEngine.respondingWithEmpty()
            val repository = repositoryWith(capture.engine)

            val result = repository.unregister(did = viewerDid, fcmToken = fcmToken)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")

            val request = capture.requests.single()
            assertEquals("/xrpc/app.bsky.notification.unregisterPush", request.url.encodedPath)
            assertEquals(
                "did:web:push.nubecita.app#bsky_notif",
                request.headers["atproto-proxy"],
            )
            val body = capture.bodies.single().asJsonObject()
            assertEquals("did:web:push.nubecita.app", body["serviceDid"]!!.jsonPrimitive.content)
            assertEquals(fcmToken, body["token"]!!.jsonPrimitive.content)
            assertEquals("android", body["platform"]!!.jsonPrimitive.content)
            assertEquals(appId, body["appId"]!!.jsonPrimitive.content)
        }

    @Test
    fun `register omits the ageRestricted field from the wire body`() =
        runTest {
            // ageRestricted is an `AtField<Boolean>` with `EncodeDefault.NEVER`
            // and `AtField.Missing` as the default. Confirming the field is
            // absent on the wire guards against a future lexicon change that
            // would start emitting it by default and cause the gateway to
            // reject the request (the v1 gateway expects exactly the four
            // documented fields).
            val capture = RecordingEngine.respondingWithEmpty()
            val repository = repositoryWith(capture.engine)

            repository.register(did = viewerDid, fcmToken = fcmToken)

            val body = capture.bodies.single().asJsonObject()
            assertNull(body["ageRestricted"], "ageRestricted should not appear on the wire")
        }

    @Test
    fun `register maps a 5xx response to Result_failure rather than throwing`() =
        runTest {
            val engine =
                MockEngine { _ ->
                    respondError(
                        status = HttpStatusCode.InternalServerError,
                        content = "{\"error\":\"GatewayDown\",\"message\":\"upstream unavailable\"}",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            val repository = repositoryWith(engine)

            val result = repository.register(did = viewerDid, fcmToken = fcmToken)

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }

    @Test
    fun `register surfaces a missing session as Result_failure carrying NoSessionException`() =
        runTest {
            val repository =
                DefaultPushRegistrationRepository(
                    xrpcClientProvider = ThrowingProvider { throw NoSessionException() },
                    appId = appId,
                    gateway = PushGatewayConfig.Nubecita,
                )

            val result = repository.register(did = viewerDid, fcmToken = fcmToken)

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull() is NoSessionException,
                "expected NoSessionException, got ${result.exceptionOrNull()}",
            )
        }

    private fun repositoryWith(engine: MockEngine): PushRegistrationRepository =
        DefaultPushRegistrationRepository(
            xrpcClientProvider = StaticProvider(authenticatedClient(engine)),
            appId = appId,
            gateway = PushGatewayConfig.Nubecita,
        )

    private fun authenticatedClient(engine: MockEngine): XrpcClient =
        XrpcClient(
            baseUrl = "https://pds.example.test",
            httpClient = HttpClient(engine),
        )

    private fun String.asJsonObject() = Json.parseToJsonElement(this).jsonObject

    private class StaticProvider(
        private val client: XrpcClient,
    ) : XrpcClientProvider {
        override suspend fun authenticated(): XrpcClient = client
    }

    private class ThrowingProvider(
        private val thrower: () -> Nothing,
    ) : XrpcClientProvider {
        override suspend fun authenticated(): XrpcClient = thrower()
    }

    private class RecordingEngine private constructor(
        val engine: MockEngine,
        val requests: List<HttpRequestData>,
        val bodies: List<String>,
    ) {
        companion object {
            fun respondingWithEmpty(): RecordingEngine {
                val capturedRequests = mutableListOf<HttpRequestData>()
                val capturedBodies = mutableListOf<String>()
                val engine =
                    MockEngine { request ->
                        capturedRequests += request
                        capturedBodies += (request.body as? TextContent)?.text.orEmpty()
                        respond(
                            // atproto-kotlin's UnitResponseSerializer accepts both
                            // an empty body and `{}` (kikin81/atproto-kotlin#119
                            // landed the empty-body fix in 9.0.1). The mock still
                            // emits `{}` to exercise the stricter shape — if a
                            // future release tightens UnitResponseSerializer back,
                            // these tests would catch it. The gateway itself
                            // returns empty body on success in production.
                            content = ByteReadChannel("{}"),
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
}
