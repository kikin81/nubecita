package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [DefaultParentFetchSource].
 *
 * Strategy mirrors `DefaultPostingRepositoryTest` and
 * `DefaultActorTypeaheadRepositoryTest`: stand up a real [XrpcClient]
 * backed by a Ktor [MockEngine] so the SDK's full
 * `FeedService.getPostThread(...)` codepath runs end-to-end against
 * deterministic responses. Asserts work on the resolved
 * `Result<ParentPostUi>` and the recorded HTTP request.
 *
 * Coverage:
 *
 *  - **Top-level post (record.reply absent)**: `rootRef == parentRef`
 *    (target IS the thread root).
 *  - **Reply post (record.reply.root present)**: `rootRef = root from
 *    record.reply.root`, `parentRef = target's own (uri, cid)`.
 *  - **NotFoundPost** thread variant → `Result.failure(ComposerError.ParentNotFound)`.
 *  - **BlockedPost** thread variant → `Result.failure(ComposerError.ParentNotFound)`.
 *  - **IOException during the fetch** → `Result.failure(ComposerError.Network)`.
 *  - **Wire format**: `getPostThread` request carries the target URI,
 *    `depth=0`, `parentHeight=0`.
 *  - **Display fields**: handle is unwrapped from the value class;
 *    blank displayName normalizes to null; text comes from the
 *    decoded record.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultParentFetchSourceTest {
    private val targetUri = AtUri("at://did:plc:alice/app.bsky.feed.post/abc")
    private val targetCid = Cid("bafabc")
    private val rootUri = AtUri("at://did:plc:alice/app.bsky.feed.post/root")
    private val rootCid = Cid("bafroot")

    // For the reply-target fixture, the reply's parent must be a
    // DIFFERENT post from the target itself — a real reply chain has
    // target → ancestor → root. The production code reads only
    // record.reply.root, but the fixture should still be structurally
    // valid so future readers don't get confused and so the SDK
    // wouldn't reject it under any forthcoming validation.
    private val ancestorUri = AtUri("at://did:plc:bob/app.bsky.feed.post/middle")
    private val ancestorCid = Cid("bafmiddle")

    @Test
    fun fetchParent_topLevelPost_rootRefEqualsParentRef() =
        runTest {
            val (_, source) =
                newSource { _ ->
                    okJson(threadResponseTopLevel())
                }

            val result = source.fetchParent(targetUri)

            assertTrue(result.isSuccess)
            val ui = result.getOrNull()!!
            // Both refs point at the target — that's the spec for a
            // top-level reply target.
            assertEquals(StrongRef(uri = targetUri, cid = targetCid), ui.parentRef)
            assertEquals(StrongRef(uri = targetUri, cid = targetCid), ui.rootRef)
            assertEquals("alice.bsky.social", ui.authorHandle)
            assertEquals("Alice", ui.authorDisplayName)
            assertEquals("Hello world from a top-level post.", ui.text)
        }

    @Test
    fun fetchParent_replyPost_rootRefComesFromRecordReplyRoot() =
        runTest {
            val (_, source) =
                newSource { _ ->
                    okJson(threadResponseReply())
                }

            val result = source.fetchParent(targetUri)

            assertTrue(result.isSuccess)
            val ui = result.getOrNull()!!
            // parentRef = target's own (uri, cid). rootRef = the root
            // ref baked into the target's `record.reply.root`.
            assertEquals(StrongRef(uri = targetUri, cid = targetCid), ui.parentRef)
            assertEquals(StrongRef(uri = rootUri, cid = rootCid), ui.rootRef)
        }

    @Test
    fun fetchParent_notFoundPost_returnsParentNotFound() =
        runTest {
            val (_, source) =
                newSource { _ ->
                    okJson(
                        """
                        {
                          "thread": {
                            "${'$'}type": "app.bsky.feed.defs#notFoundPost",
                            "uri": "${targetUri.raw}",
                            "notFound": true
                          }
                        }
                        """.trimIndent(),
                    )
                }

            val result = source.fetchParent(targetUri)

            assertTrue(result.isFailure)
            assertEquals(ComposerError.ParentNotFound, result.exceptionOrNull())
        }

    @Test
    fun fetchParent_blockedPost_returnsParentNotFound() =
        runTest {
            val (_, source) =
                newSource { _ ->
                    okJson(
                        """
                        {
                          "thread": {
                            "${'$'}type": "app.bsky.feed.defs#blockedPost",
                            "uri": "${targetUri.raw}",
                            "blocked": true,
                            "author": {
                              "did": "did:plc:alice",
                              "viewer": null
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                }

            val result = source.fetchParent(targetUri)

            assertTrue(result.isFailure)
            assertEquals(ComposerError.ParentNotFound, result.exceptionOrNull())
        }

    @Test
    fun fetchParent_ioException_returnsNetworkFailure() =
        runTest {
            val (_, source) =
                newSource { _ ->
                    throw IOException("simulated socket failure")
                }

            val result = source.fetchParent(targetUri)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertTrue(
                cause is ComposerError.Network,
                "expected ComposerError.Network, was ${cause?.javaClass?.simpleName}",
            )
        }

    @Test
    fun fetchParent_wireFormat_carriesUriDepth0ParentHeight0() =
        runTest {
            val (engine, source) =
                newSource { _ ->
                    okJson(threadResponseTopLevel())
                }

            source.fetchParent(targetUri)

            val recorded = engine.requestHistory.single()
            assertTrue(
                recorded.url.encodedPath.endsWith("getPostThread"),
                "wrong NSID: ${recorded.url}",
            )
            assertEquals(targetUri.raw, recorded.url.parameters["uri"])
            assertEquals("0", recorded.url.parameters["depth"])
            assertEquals("0", recorded.url.parameters["parentHeight"])
        }

    @Test
    fun fetchParent_recordDecodeFailure_returnsRecordCreationFailed() =
        runTest {
            // Regression for the PR #125 review: if the target's
            // record can't decode as a Post, falling back to
            // "target is the root" would silently corrupt the reply
            // ref when the target was actually a reply. Decode
            // failure now surfaces as RecordCreationFailed instead
            // — fail loud rather than construct a misthreaded post.
            val (_, source) =
                newSource { _ ->
                    okJson(
                        """
                        {
                          "thread": {
                            "${'$'}type": "app.bsky.feed.defs#threadViewPost",
                            "post": {
                              "${'$'}type": "app.bsky.feed.defs#postView",
                              "uri": "${targetUri.raw}",
                              "cid": "${targetCid.raw}",
                              "author": {
                                "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                                "did": "did:plc:alice",
                                "handle": "alice.bsky.social"
                              },
                              "record": {
                                "${'$'}type": "app.bsky.feed.post"
                              },
                              "indexedAt": "2026-05-05T00:00:00Z"
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                }

            val result = source.fetchParent(targetUri)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertTrue(
                cause is ComposerError.RecordCreationFailed,
                "expected RecordCreationFailed, was ${cause?.javaClass?.simpleName}",
            )
        }

    @Test
    fun fetchParent_blankDisplayName_normalizesToNull() =
        runTest {
            // Wire returns displayName="" (blank). Boundary contract
            // says null means "no display name"; downstream consumers
            // shouldn't have to re-check `.isBlank()` to render the
            // handle fallback.
            val (_, source) =
                newSource { _ ->
                    okJson(threadResponseTopLevel(displayName = ""))
                }

            val result = source.fetchParent(targetUri)

            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrNull()!!.authorDisplayName)
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
    ): Pair<MockEngine, DefaultParentFetchSource> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val source =
            DefaultParentFetchSource(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        override suspend fun authenticated(): XrpcClient = xrpcClient
                    },
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to source
    }

    /**
     * Synthetic `getPostThread` response for a top-level post (the
     * target IS the thread root — `record.reply` is absent on the
     * wire).
     */
    private fun threadResponseTopLevel(displayName: String? = "Alice"): String {
        val displayNameField = displayName?.let { ",\"displayName\":\"$it\"" } ?: ""
        return """
            {
              "thread": {
                "${'$'}type": "app.bsky.feed.defs#threadViewPost",
                "post": {
                  "${'$'}type": "app.bsky.feed.defs#postView",
                  "uri": "${targetUri.raw}",
                  "cid": "${targetCid.raw}",
                  "author": {
                    "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                    "did": "did:plc:alice",
                    "handle": "alice.bsky.social"$displayNameField
                  },
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "Hello world from a top-level post.",
                    "createdAt": "2026-05-05T00:00:00Z"
                  },
                  "indexedAt": "2026-05-05T00:00:00Z"
                }
              }
            }
            """.trimIndent()
    }

    /**
     * Synthetic `getPostThread` response for a reply post — target
     * carries `record.reply.root` pointing at the thread root.
     */
    private fun threadResponseReply(): String =
        """
        {
          "thread": {
            "${'$'}type": "app.bsky.feed.defs#threadViewPost",
            "post": {
              "${'$'}type": "app.bsky.feed.defs#postView",
              "uri": "${targetUri.raw}",
              "cid": "${targetCid.raw}",
              "author": {
                "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                "did": "did:plc:alice",
                "handle": "alice.bsky.social",
                "displayName": "Alice"
              },
              "record": {
                "${'$'}type": "app.bsky.feed.post",
                "text": "This is a reply.",
                "createdAt": "2026-05-05T00:00:00Z",
                "reply": {
                  "parent": {"uri": "${ancestorUri.raw}", "cid": "${ancestorCid.raw}"},
                  "root": {"uri": "${rootUri.raw}", "cid": "${rootCid.raw}"}
                }
              },
              "indexedAt": "2026-05-05T00:00:00Z"
            }
          }
        }
        """.trimIndent()
}
