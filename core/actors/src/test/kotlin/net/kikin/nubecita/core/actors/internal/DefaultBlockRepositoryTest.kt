package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.runtime.NoAuth
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultBlockRepository].
 *
 * Stand up a real [XrpcClient] backed by a Ktor [MockEngine] so the SDK's
 * full `com.atproto.repo.createRecord` / `deleteRecord` and
 * `app.bsky.graph.getBlocks` codepaths run end-to-end against deterministic
 * responses. Mirrors the harness in [DefaultMuteRepositoryTest] /
 * [DefaultActorRepositoryTest] (its two tested siblings in this module) and the
 * record-creation shape of `DefaultLikeRepostRepositoryTest`.
 *
 * Coverage:
 *  - blockActor success → createRecord with app.bsky.graph.block collection,
 *    viewer DID as repo, target DID as the record subject.
 *  - blockActor without a session → NoSessionException, no request sent.
 *  - blockedAccounts success → maps ProfileView → BlockedAccount.
 *  - blockedAccounts skips a block with no `viewer.blocking` record URI.
 *  - blockedAccounts blank displayName → normalized to null.
 *  - blockedAccounts pages through cursors and accumulates every page.
 *  - blockedAccounts stops at the MAX_BLOCK_PAGES safety cap.
 *  - unblockActor success → deleteRecord with collection/repo/rkey parsed from URI.
 *  - unblockActor with a malformed / rkey-less URI → failure, no request sent.
 *  - 4xx/network error → Result.failure for every method.
 *  - CancellationException propagates (not swallowed into Result.failure).
 */
class DefaultBlockRepositoryTest {
    private val viewerDid = "did:plc:viewer123"
    private val viewerHandle = "viewer.test"

    // -------------------------------------------------------------------------
    // blockActor
    // -------------------------------------------------------------------------

    @Test
    fun blockActor_success_sendsCreateRecordWithBlockCollectionAndSubject() =
        runTest {
            val capture = RecordingEngine.respondingWith(CREATE_RECORD_RESPONSE)
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.blockActor("did:plc:target789")

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.createRecord", request.url.encodedPath)
            val body = jsonObjectBody(capture.bodies.single())
            assertEquals("app.bsky.graph.block", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            val record = body["record"]!!.jsonObject
            assertEquals("app.bsky.graph.block", record["\$type"]!!.jsonPrimitive.content)
            // The block record's `subject` is the target account's DID (a plain
            // string), NOT a strong ref — distinct from like/repost records.
            assertEquals("did:plc:target789", record["subject"]!!.jsonPrimitive.content)
            val createdAt = record["createdAt"]!!.jsonPrimitive.content
            assertTrue(createdAt.contains('T'), "createdAt should be ISO 8601, got $createdAt")
        }

    @Test
    fun blockActor_noSession_returnsFailureAndSendsNoRequest() =
        runTest {
            // `currentViewerDid()` is the first statement inside runCatching, so a
            // SignedOut session short-circuits with NoSessionException before any
            // network call — even though the engine would happily respond.
            val capture = RecordingEngine.respondingWith(CREATE_RECORD_RESPONSE)
            val repo = newRepo(capture.engine, SessionState.SignedOut)

            val result = repo.blockActor("did:plc:target789")

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull() is NoSessionException,
                "expected NoSessionException, got ${result.exceptionOrNull()}",
            )
            assertTrue(capture.requests.isEmpty(), "no request should be sent without a session")
        }

    @Test
    fun blockActor_networkFailure_returnsFailure() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.BadRequest) }
            val repo = newRepo(engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.blockActor("did:plc:target789")

            assertTrue(result.isFailure)
        }

    @Test
    fun blockActor_cancellation_propagates() {
        val engine = MockEngine { throw CancellationException("scope cancelled") }
        val repo = newRepo(engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.blockActor("did:plc:target789") }
        }
    }

    // -------------------------------------------------------------------------
    // blockedAccounts
    // -------------------------------------------------------------------------

    @Test
    fun blockedAccounts_success_mapsProfileViewToBlockedAccount() =
        runTest {
            val capture =
                RecordingEngine.respondingWith(
                    getBlocksResponse(
                        cursor = null,
                        profileView(
                            did = "did:plc:blocked1",
                            handle = "blocked1.bsky.social",
                            displayName = "Blocked One",
                            avatar = "https://cdn.example/1.jpg",
                            blockUri = "at://$viewerDid/app.bsky.graph.block/rkey1",
                        ),
                    ),
                )
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.blockedAccounts()

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val account = result.getOrThrow().single()
            assertEquals("did:plc:blocked1", account.did)
            assertEquals("blocked1.bsky.social", account.handle)
            assertEquals("Blocked One", account.displayName)
            assertEquals("https://cdn.example/1.jpg", account.avatarUrl)
            assertEquals("at://$viewerDid/app.bsky.graph.block/rkey1", account.blockUri)
        }

    @Test
    fun blockedAccounts_skipsBlockWithoutBlockUri() =
        runTest {
            // A ProfileView whose `viewer.blocking` record URI is absent can't be
            // unblocked (nothing to delete), so it must not be listed.
            val capture =
                RecordingEngine.respondingWith(
                    getBlocksResponse(
                        cursor = null,
                        profileView(
                            did = "did:plc:withuri",
                            handle = "withuri.bsky.social",
                            blockUri = "at://$viewerDid/app.bsky.graph.block/rkey1",
                        ),
                        profileView(
                            did = "did:plc:nouri",
                            handle = "nouri.bsky.social",
                            blockUri = null,
                        ),
                    ),
                )
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val accounts = repo.blockedAccounts().getOrThrow()

            assertEquals(1, accounts.size)
            assertEquals("did:plc:withuri", accounts.single().did)
        }

    @Test
    fun blockedAccounts_blankDisplayName_normalizedToNull() =
        runTest {
            val capture =
                RecordingEngine.respondingWith(
                    getBlocksResponse(
                        cursor = null,
                        profileView(
                            did = "did:plc:blank",
                            handle = "blank.bsky.social",
                            displayName = "   ",
                            blockUri = "at://$viewerDid/app.bsky.graph.block/rkey1",
                        ),
                    ),
                )
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val account = repo.blockedAccounts().getOrThrow().single()

            assertNull(account.displayName, "blank displayName should normalize to null")
        }

    @Test
    fun blockedAccounts_pagesThroughCursorsAndAccumulates() =
        runTest {
            // First page carries a cursor → the repo must fetch a second page and
            // concatenate. Second page has no cursor → pagination stops.
            val capture =
                RecordingEngine.respondingWith(
                    getBlocksResponse(
                        cursor = "page2",
                        profileView(
                            did = "did:plc:blocked1",
                            handle = "blocked1.bsky.social",
                            blockUri = "at://$viewerDid/app.bsky.graph.block/rkey1",
                        ),
                    ),
                    getBlocksResponse(
                        cursor = null,
                        profileView(
                            did = "did:plc:blocked2",
                            handle = "blocked2.bsky.social",
                            blockUri = "at://$viewerDid/app.bsky.graph.block/rkey2",
                        ),
                    ),
                )
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val accounts = repo.blockedAccounts().getOrThrow()

            assertEquals(listOf("did:plc:blocked1", "did:plc:blocked2"), accounts.map { it.did })
            assertEquals(2, capture.requests.size, "should page exactly twice")
            // The second request forwards the first page's cursor.
            assertTrue(
                capture.requests[1]
                    .url
                    .toString()
                    .contains("cursor=page2"),
                "second page request should carry cursor=page2",
            )
        }

    @Test
    fun blockedAccounts_stopsAtMaxPageSafetyCap() =
        runTest {
            // A pathological server that always returns a non-null cursor must not
            // loop forever — MAX_BLOCK_PAGES (20) bounds it. Every response reuses
            // the single cursor-bearing body (see RecordingEngine's "reuse last").
            val capture =
                RecordingEngine.respondingWith(
                    getBlocksResponse(
                        cursor = "always-more",
                        profileView(
                            did = "did:plc:blocked",
                            handle = "blocked.bsky.social",
                            blockUri = "at://$viewerDid/app.bsky.graph.block/rkey",
                        ),
                    ),
                )
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.blockedAccounts()

            assertTrue(result.isSuccess)
            assertEquals(20, capture.requests.size, "pagination should stop at MAX_BLOCK_PAGES")
        }

    @Test
    fun blockedAccounts_networkFailure_returnsFailure() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.BadRequest) }
            val repo = newRepo(engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.blockedAccounts()

            assertTrue(result.isFailure)
        }

    @Test
    fun blockedAccounts_cancellation_propagates() {
        val engine = MockEngine { throw CancellationException("scope cancelled") }
        val repo = newRepo(engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.blockedAccounts() }
        }
    }

    // -------------------------------------------------------------------------
    // unblockActor
    // -------------------------------------------------------------------------

    @Test
    fun unblockActor_success_sendsDeleteRecordWithParsedRkey() =
        runTest {
            val capture = RecordingEngine.respondingWith(DELETE_RECORD_RESPONSE)
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.unblockActor("at://$viewerDid/app.bsky.graph.block/rkey1")

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.deleteRecord", request.url.encodedPath)
            val body = jsonObjectBody(capture.bodies.single())
            assertEquals("app.bsky.graph.block", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            assertEquals("rkey1", body["rkey"]!!.jsonPrimitive.content)
        }

    @Test
    fun unblockActor_malformedUri_returnsFailureAndSendsNoRequest() =
        runTest {
            val capture = RecordingEngine.respondingWith(DELETE_RECORD_RESPONSE)
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.unblockActor("not-an-at-uri")

            assertTrue(result.isFailure)
            assertTrue(capture.requests.isEmpty(), "no request should be sent for a malformed URI")
        }

    @Test
    fun unblockActor_uriWithoutRkey_returnsFailureAndSendsNoRequest() =
        runTest {
            // at://<repo>/<collection> with no rkey addresses no record — the
            // requireNotNull(parts.rkey) guard fails before the engine is touched.
            val capture = RecordingEngine.respondingWith(DELETE_RECORD_RESPONSE)
            val repo = newRepo(capture.engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.unblockActor("at://$viewerDid/app.bsky.graph.block")

            assertTrue(result.isFailure)
            assertTrue(capture.requests.isEmpty(), "no request should be sent without an rkey")
        }

    @Test
    fun unblockActor_networkFailure_returnsFailure() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.BadRequest) }
            val repo = newRepo(engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

            val result = repo.unblockActor("at://$viewerDid/app.bsky.graph.block/rkey1")

            assertTrue(result.isFailure)
        }

    @Test
    fun unblockActor_cancellation_propagates() {
        val engine = MockEngine { throw CancellationException("scope cancelled") }
        val repo = newRepo(engine, SessionState.SignedIn(handle = viewerHandle, did = viewerDid))

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.unblockActor("at://$viewerDid/app.bsky.graph.block/rkey1") }
        }
    }

    // -------------------------------------------------------------------------
    // Harness helpers
    // -------------------------------------------------------------------------

    private fun newRepo(
        engine: MockEngine,
        session: SessionState,
    ): DefaultBlockRepository {
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = xrpcClient
            }
        return DefaultBlockRepository(
            xrpcClientProvider = provider,
            sessionStateProvider = FakeSessionStateProvider(session),
            dispatcher = Dispatchers.Unconfined,
        )
    }

    private fun jsonObjectBody(body: String) = Json.parseToJsonElement(body).jsonObject

    private fun profileView(
        did: String,
        handle: String,
        displayName: String? = null,
        avatar: String? = null,
        blockUri: String?,
    ): String {
        val fields =
            buildList {
                add("\"did\":\"$did\"")
                add("\"handle\":\"$handle\"")
                if (displayName != null) add("\"displayName\":\"$displayName\"")
                if (avatar != null) add("\"avatar\":\"$avatar\"")
                if (blockUri != null) add("\"viewer\":{\"blocking\":\"$blockUri\"}")
            }
        return "{${fields.joinToString(",")}}"
    }

    private fun getBlocksResponse(
        cursor: String?,
        vararg blocks: String,
    ): String {
        val cursorField = if (cursor != null) "\"cursor\":\"$cursor\"," else ""
        return "{$cursorField\"blocks\":[${blocks.joinToString(",")}]}"
    }

    private companion object {
        const val CREATE_RECORD_RESPONSE =
            """{"uri":"at://did:plc:viewer123/app.bsky.graph.block/rkey1","cid":"bafyreiablock","commit":null,"validationStatus":"valid"}"""
        const val DELETE_RECORD_RESPONSE = """{"commit":null}"""
    }
}

/**
 * Captures every request + body the [MockEngine] receives so tests can assert
 * URL and body shape, and replays a sequence of response bodies (one per
 * request) to drive pagination. When requests outnumber the supplied bodies the
 * last body is reused — this is what lets the MAX_BLOCK_PAGES cap test feed an
 * endless cursor from a single body. Mirrors the RecordingEngine in
 * DefaultLikeRepostRepositoryTest.
 */
private class RecordingEngine private constructor(
    val engine: MockEngine,
    val requests: List<HttpRequestData>,
    val bodies: List<String>,
) {
    companion object {
        fun respondingWith(vararg responseBodies: String): RecordingEngine {
            require(responseBodies.isNotEmpty()) { "at least one response body is required" }
            val capturedRequests = mutableListOf<HttpRequestData>()
            val capturedBodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    val index = minOf(capturedRequests.size, responseBodies.size - 1)
                    capturedRequests += request
                    capturedBodies += (request.body as? TextContent)?.text.orEmpty()
                    respond(
                        content = ByteReadChannel(responseBodies[index]),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            return RecordingEngine(engine = engine, requests = capturedRequests, bodies = capturedBodies)
        }
    }
}

private class FakeSessionStateProvider(
    initial: SessionState,
) : SessionStateProvider {
    override val state: StateFlow<SessionState> = MutableStateFlow(initial)

    override suspend fun refresh() {
        // no-op; tests pin the session state at construction
    }
}
