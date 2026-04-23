package net.kikin.nubecita.data

import io.github.kikin81.atproto.com.atproto.identity.IdentityService
import io.github.kikin81.atproto.com.atproto.identity.ResolveHandleRequest
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class AtProtoClientTest {
    @Test
    fun resolveHandle_bskyApp_returnsDid() =
        runBlocking {
            val client =
                XrpcClient(
                    baseUrl = "https://public.api.bsky.app",
                    httpClient = HttpClient(CIO),
                )

            val response =
                IdentityService(client).resolveHandle(
                    ResolveHandleRequest(handle = Handle("bsky.app")),
                )

            assertTrue(
                "expected did:plc:… but got ${response.did.raw}",
                response.did.raw.startsWith("did:plc:"),
            )
        }
}
