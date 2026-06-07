package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.runtime.AtUri
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
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.posting.ComposerError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultQuotePostFetcher].
 *
 * Strategy mirrors [DefaultParentFetchSourceTest]: a real [XrpcClient] over a
 * Ktor [MockEngine] runs the SDK codepath end-to-end.
 *
 * Core regression (nubecita-8g28.9): a pasted `bsky.app/profile/<handle>/post/…`
 * link yields an at-uri with a HANDLE authority. `app.bsky.feed.getPosts` looks
 * records up by DID, so the fetcher must resolve the handle to a DID first —
 * otherwise getPosts returns an empty set and the quote shows "couldn't load".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultQuotePostFetcherTest {
    @Test
    fun fetchQuote_handleAuthority_resolvesToDid_thenGetPostsByDid() =
        runTest {
            val handleUri = AtUri("at://belgianboolean.bsky.social/app.bsky.feed.post/3mnnxxnelak2j")
            var resolveCalled = false
            var getPostsUri: String? = null
            val (_, source) =
                newSource { request ->
                    when {
                        request.url.encodedPath.endsWith("resolveHandle") -> {
                            resolveCalled = true
                            assertEquals("belgianboolean.bsky.social", request.url.parameters["handle"])
                            okJson("""{"did":"did:plc:belgian"}""")
                        }
                        request.url.encodedPath.endsWith("getPosts") -> {
                            getPostsUri = request.url.parameters["uris"]
                            okJson(postsResponse("at://did:plc:belgian/app.bsky.feed.post/3mnnxxnelak2j"))
                        }
                        else -> error("unexpected request: ${request.url}")
                    }
                }

            val result = source.fetchQuote(handleUri)

            assertTrue(result.isSuccess, "expected success, was ${result.exceptionOrNull()}")
            assertTrue(resolveCalled, "handle authority must be resolved before getPosts")
            assertTrue(
                getPostsUri?.contains("did:plc:belgian") == true,
                "getPosts should query the resolved DID uri, was: $getPostsUri",
            )
            assertFalse(
                getPostsUri?.contains("belgianboolean.bsky.social") == true,
                "handle authority leaked into the getPosts query",
            )
            assertEquals(
                "at://did:plc:belgian/app.bsky.feed.post/3mnnxxnelak2j",
                result
                    .getOrNull()!!
                    .ref.uri.raw,
            )
        }

    @Test
    fun fetchQuote_didAuthority_skipsResolveHandle() =
        runTest {
            val didUri = AtUri("at://did:plc:bob/app.bsky.feed.post/xyz")
            var resolveCalled = false
            val (_, source) =
                newSource { request ->
                    when {
                        request.url.encodedPath.endsWith("resolveHandle") -> {
                            resolveCalled = true
                            okJson("""{"did":"did:plc:bob"}""")
                        }
                        request.url.encodedPath.endsWith("getPosts") -> okJson(postsResponse(didUri.raw))
                        else -> error("unexpected request: ${request.url}")
                    }
                }

            val result = source.fetchQuote(didUri)

            assertTrue(result.isSuccess)
            assertFalse(resolveCalled, "a DID authority must not trigger a resolveHandle round-trip")
        }

    @Test
    fun fetchQuote_emptyResult_returnsParentNotFound() =
        runTest {
            val didUri = AtUri("at://did:plc:bob/app.bsky.feed.post/xyz")
            val (_, source) =
                newSource { request ->
                    if (request.url.encodedPath.endsWith("getPosts")) okJson("""{"posts":[]}""") else error("unexpected")
                }

            val result = source.fetchQuote(didUri)

            assertTrue(result.isFailure)
            assertEquals(ComposerError.ParentNotFound, result.exceptionOrNull())
        }

    // ---------- harness ----------

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private fun newSource(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultQuotePostFetcher> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val source =
            DefaultQuotePostFetcher(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        override suspend fun authenticated(): XrpcClient = xrpcClient
                    },
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to source
    }

    private fun postsResponse(uri: String): String =
        """
        {
          "posts": [
            {
              "${'$'}type": "app.bsky.feed.defs#postView",
              "uri": "$uri",
              "cid": "bafquote",
              "author": {
                "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                "did": "did:plc:belgian",
                "handle": "belgianboolean.bsky.social",
                "displayName": "Belgian Boolean"
              },
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "the quoted post body",
                "createdAt": "2026-05-05T00:00:00Z"
              },
              "indexedAt": "2026-05-05T00:00:00Z"
            }
          ]
        }
        """.trimIndent()
}
