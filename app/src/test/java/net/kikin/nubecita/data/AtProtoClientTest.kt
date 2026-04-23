package net.kikin.nubecita.data

import io.github.kikin81.atproto.com.atproto.identity.IdentityService
import io.github.kikin81.atproto.com.atproto.identity.ResolveHandleRequest
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class AtProtoClientTest {
    // Opt-in gate: this is the smoke test wired to hit public.api.bsky.app
    // directly. Without the gate, every PR's :app:jacocoTestReport job would
    // depend on the upstream AppView being reachable, turning transient
    // Bluesky outages into unrelated-PR failures. Instrumented equivalent
    // lands with nubecita-16a.
    @Before
    fun skipUnlessIntegrationEnabled() {
        assumeTrue(
            "Set ATPROTO_INTEGRATION_TESTS=1 to run network-dependent smoke tests",
            System.getenv("ATPROTO_INTEGRATION_TESTS") == "1",
        )
    }

    @Test
    fun resolveHandle_bskyApp_returnsDid() =
        runBlocking {
            val httpClient =
                HttpClient(OkHttp) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 30_000
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 30_000
                    }
                }
            try {
                val client =
                    XrpcClient(
                        baseUrl = "https://public.api.bsky.app",
                        httpClient = httpClient,
                    )

                val response =
                    IdentityService(client).resolveHandle(
                        ResolveHandleRequest(handle = Handle("bsky.app")),
                    )

                assertTrue(
                    "expected did:plc:… but got ${response.did.raw}",
                    response.did.raw.startsWith("did:plc:"),
                )
            } finally {
                httpClient.close()
            }
        }
}
