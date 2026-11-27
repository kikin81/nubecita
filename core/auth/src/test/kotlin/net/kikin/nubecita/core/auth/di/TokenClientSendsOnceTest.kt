package net.kikin.nubecita.core.auth.di

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A token POST spends a single-use refresh token, so the client that sends
 * it must put each request on the wire once. These run the real OkHttp
 * client against a local server and count what arrives.
 *
 * Each case has a control with a default client: it shows the re-send is
 * real, so the assertion on the token client is not passing for some other
 * reason.
 */
class TokenClientSendsOnceTest {
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private lateinit var server: HttpServer

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        // First answer: "unavailable, retry now". Any later one: success.
        server.createContext("/unavailable") { exchange ->
            val first = hit("/unavailable") == 1
            exchange.requestBody.readBytes()
            if (first) exchange.responseHeaders.add("Retry-After", "0")
            exchange.sendResponseHeaders(if (first) 503 else 200, -1)
            exchange.close()
        }
        server.createContext("/redirect") { exchange ->
            hit("/redirect")
            exchange.requestBody.readBytes()
            exchange.responseHeaders.add("Location", "/target")
            // 307 keeps the method and body, so following it re-sends the POST.
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.createContext("/target") { exchange ->
            hit("/target")
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    private fun hit(path: String): Int = hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()

    private fun hits(path: String): Int = hits[path]?.get() ?: 0

    private fun OkHttpClient.post(path: String): Int =
        newCall(
            Request
                .Builder()
                .url("http://127.0.0.1:${server.address.port}$path")
                .post("grant_type=refresh_token".toRequestBody())
                .build(),
        ).execute().use { it.code }

    @Test
    fun `a 503 asking for an immediate retry is handed back, not retried`() {
        val status = NetworkEngineModule.tokenEndpointOkHttpClient().post("/unavailable")

        assertEquals(503, status)
        assertEquals(1, hits("/unavailable"))
    }

    @Test
    fun `control - a default client sends that request twice`() {
        val status = OkHttpClient().post("/unavailable")

        assertEquals(200, status)
        assertEquals(2, hits("/unavailable"))
    }

    @Test
    fun `a redirect is handed back, not followed`() {
        val status = NetworkEngineModule.tokenEndpointOkHttpClient().post("/redirect")

        assertEquals(307, status)
        assertEquals(1, hits("/redirect"))
        assertEquals(0, hits("/target"))
    }

    @Test
    fun `control - a default client re-sends the POST to the redirect target`() {
        val status = OkHttpClient().post("/redirect")

        assertEquals(200, status)
        assertEquals(1, hits("/target"))
    }
}
