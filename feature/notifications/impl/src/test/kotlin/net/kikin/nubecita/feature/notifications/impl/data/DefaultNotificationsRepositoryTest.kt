package net.kikin.nubecita.feature.notifications.impl.data

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.data.models.NotificationFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultNotificationsRepositoryTest {
    @Test
    fun `fetchPage returns empty page when notifications array is empty and does not call getPosts`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse = """{"notifications":[],"cursor":null}"""
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            val page = repo.fetchPage(NotificationFilter.All, cursor = null).getOrThrow()

            assertEquals(0, page.items.size)
            assertNull(page.nextCursor)
            assertEquals(1, recorder.listNotificationsCalls.size)
            assertEquals(0, recorder.getPostsCalls.size, "empty page MUST NOT issue getPosts")
        }

    @Test
    fun `fetchPage with engagement notifications hydrates subject posts via reasonSubject`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse =
                listNotificationsBody(
                    notifications =
                        listOf(
                            notificationJson(
                                actorDid = "did:plc:alice",
                                reason = "like",
                                reasonSubject = "at://did:plc:user/app.bsky.feed.post/p1",
                                uri = "at://did:plc:alice/app.bsky.feed.like/r1",
                            ),
                            notificationJson(
                                actorDid = "did:plc:bob",
                                reason = "like",
                                reasonSubject = "at://did:plc:user/app.bsky.feed.post/p2",
                                uri = "at://did:plc:bob/app.bsky.feed.like/r2",
                            ),
                        ),
                )
            // Stub getPosts to return whatever URIs were requested back as valid PostViews.
            recorder.getPostsBuilder = { uris -> getPostsBody(uris.map { postViewJson(uri = it, authorDid = "did:plc:user") }) }
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            val page = repo.fetchPage(NotificationFilter.All, cursor = null).getOrThrow()

            // Both engagement notifications used reasonSubject as the hydration key — verified by inspecting the
            // single getPosts call's URI set.
            assertEquals(1, recorder.getPostsCalls.size)
            val requestedUris = recorder.getPostsCalls.single()
            assertEquals(
                setOf(
                    "at://did:plc:user/app.bsky.feed.post/p1",
                    "at://did:plc:user/app.bsky.feed.post/p2",
                ),
                requestedUris.toSet(),
            )
            // Each row gets its own hydrated subjectPost (mapper validation lives in NotificationsMapperTest;
            // here we just confirm the routing hooked up).
            page.items.forEach { row ->
                assertNotNull(row.subjectPost, "engagement row should have subjectPost hydrated")
            }
        }

    @Test
    fun `fetchPage with content-bearing notifications hydrates via uri`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse =
                listNotificationsBody(
                    notifications =
                        listOf(
                            notificationJson(
                                actorDid = "did:plc:alice",
                                reason = "reply",
                                // reasonSubject is the user's post; the row is about the actor's new reply.
                                reasonSubject = "at://did:plc:user/app.bsky.feed.post/parent",
                                uri = "at://did:plc:alice/app.bsky.feed.post/reply-1",
                            ),
                            notificationJson(
                                actorDid = "did:plc:bob",
                                reason = "subscribed-post",
                                reasonSubject = null,
                                uri = "at://did:plc:bob/app.bsky.feed.post/new-1",
                            ),
                        ),
                )
            recorder.getPostsBuilder = { uris -> getPostsBody(uris.map { postViewJson(uri = it, authorDid = "did:plc:author") }) }
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            repo.fetchPage(NotificationFilter.All, cursor = null).getOrThrow()

            assertEquals(1, recorder.getPostsCalls.size)
            val requested = recorder.getPostsCalls.single().toSet()
            assertEquals(
                setOf(
                    "at://did:plc:alice/app.bsky.feed.post/reply-1",
                    "at://did:plc:bob/app.bsky.feed.post/new-1",
                ),
                requested,
                "content-bearing reasons must hydrate via the notification `uri`, never `reasonSubject`",
            )
        }

    @Test
    fun `fetchPage chunks getPosts when hydration set exceeds 25 URIs`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            // 30 distinct same-reason notifications with 30 distinct reasonSubjects → 30 URIs → 2 batches (25 + 5).
            val notifications =
                (1..30).map { i ->
                    notificationJson(
                        actorDid = "did:plc:actor$i",
                        reason = "like",
                        reasonSubject = "at://did:plc:user/app.bsky.feed.post/p$i",
                        uri = "at://did:plc:actor$i/app.bsky.feed.like/r$i",
                    )
                }
            recorder.listNotificationsResponse = listNotificationsBody(notifications)
            recorder.getPostsBuilder = { uris -> getPostsBody(uris.map { postViewJson(uri = it, authorDid = "did:plc:user") }) }
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            repo.fetchPage(NotificationFilter.All, cursor = null).getOrThrow()

            assertEquals(2, recorder.getPostsCalls.size, "30 URIs must split across 2 batches (cap=25)")
            assertEquals(25, recorder.getPostsCalls[0].size, "first batch should be full (25)")
            assertEquals(5, recorder.getPostsCalls[1].size, "second batch carries the remainder (5)")
            // The two batches together cover every URI exactly once.
            val unioned = recorder.getPostsCalls.flatten().toSet()
            assertEquals(30, unioned.size)
        }

    @Test
    fun `fetchPage skips getPosts entirely when no reasons require hydration`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse =
                listNotificationsBody(
                    notifications =
                        listOf(
                            notificationJson(
                                actorDid = "did:plc:alice",
                                reason = "follow",
                                reasonSubject = null,
                                uri = "at://did:plc:alice/app.bsky.graph.follow/r1",
                            ),
                            notificationJson(
                                actorDid = "did:plc:bob",
                                reason = "verified",
                                reasonSubject = null,
                                uri = "at://did:plc:bob/app.bsky.graph.verification/v1",
                            ),
                        ),
                )
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            repo.fetchPage(NotificationFilter.All, cursor = null).getOrThrow()

            assertEquals(0, recorder.getPostsCalls.size, "follow + verified produce no hydration URIs")
        }

    @Test
    fun `fetchPage passes filter reasons array to listNotifications`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse = """{"notifications":[]}"""
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            repo.fetchPage(NotificationFilter.Mentions, cursor = null).getOrThrow()

            val params = recorder.listNotificationsCalls.single()
            // The reasons[] array is encoded as repeated `reasons=` query parameters.
            assertEquals(
                listOf("mention", "reply", "quote"),
                params.getAll("reasons"),
            )
        }

    @Test
    fun `fetchPage omits reasons param entirely for All filter`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse = """{"notifications":[]}"""
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            repo.fetchPage(NotificationFilter.All, cursor = null).getOrThrow()

            val params = recorder.listNotificationsCalls.single()
            assertNull(params.getAll("reasons"), "All filter MUST NOT send any `reasons=` parameter")
        }

    @Test
    fun `fetchPage forwards cursor on follow-up pages`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse = """{"notifications":[],"cursor":"page-3-cursor"}"""
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            val page = repo.fetchPage(NotificationFilter.All, cursor = "page-2-cursor").getOrThrow()

            assertEquals("page-2-cursor", recorder.listNotificationsCalls.single().get("cursor"))
            assertEquals("page-3-cursor", page.nextCursor)
        }

    @Test
    fun `fetchPage failure on network error surfaces as Result_failure`() =
        runTest {
            val engine = MockEngine { throw IOException("simulated network failure") }
            val repo = makeRepo(engine = engine, testScheduler = testScheduler)

            val result = repo.fetchPage(NotificationFilter.All, cursor = null)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertNotNull(cause)
            assertTrue(cause is IOException || cause?.cause is IOException, "expected IOException in cause chain, got $cause")
        }

    @Test
    fun `fetchPage NoSessionException propagates via Result_failure`() =
        runTest {
            val repo =
                DefaultNotificationsRepository(
                    xrpcClientProvider = FakeXrpcClientProvider { throw NoSessionException() },
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            val result = repo.fetchPage(NotificationFilter.All, cursor = null)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSessionException)
        }

    @Test
    fun `markSeen sends updateSeen with seenAt timestamp`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            // updateSeen has an empty body response; the lexicon defines no output schema.
            recorder.updateSeenResponse = "{}"
            val repo = makeRepo(recorder, testScheduler = testScheduler)
            val seenAt = Instant.parse("2026-05-27T05:00:00Z")

            val result = repo.markSeen(seenAt)

            assertTrue(result.isSuccess, "markSeen should succeed; got ${result.exceptionOrNull()}")
            val request = recorder.updateSeenCalls.single()
            // The body is JSON `{"seenAt": "..."}` — verify the timestamp is the ISO-8601 form.
            assertTrue(request.body.contains("2026-05-27T05:00:00Z"), "updateSeen body must include the seenAt timestamp")
        }

    @Test
    fun `markSeen surfaces 5xx as Result_failure`() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("updateSeen")) {
                        respondError(HttpStatusCode.InternalServerError)
                    } else {
                        error("unexpected ${request.url}")
                    }
                }
            val repo = makeRepo(engine = engine, testScheduler = testScheduler)

            val result = repo.markSeen(Instant.parse("2026-05-27T05:00:00Z"))
            assertTrue(result.isFailure)
        }

    @Test
    fun `unreadCount returns the server-reported count`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.getUnreadCountResponse = """{"count":7}"""
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            val result = repo.unreadCount()

            assertEquals(7, result.getOrThrow())
        }

    @Test
    fun `unreadCount saturates Long overflow to Int_MAX_VALUE`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            // pathological large count; the badge displays "99+" anyway via M3 BadgedBox overflow.
            recorder.getUnreadCountResponse = """{"count":9999999999}"""
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            assertEquals(Int.MAX_VALUE, repo.unreadCount().getOrThrow())
        }

    @Test
    fun `unreadCount surfaces failure for non-2xx`() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("getUnreadCount")) {
                        respondError(HttpStatusCode.Unauthorized)
                    } else {
                        error("unexpected ${request.url}")
                    }
                }
            val repo = makeRepo(engine = engine, testScheduler = testScheduler)

            val result = repo.unreadCount()
            assertTrue(result.isFailure)
        }

    @Test
    fun `getPosts failure during fetchPage propagates as Result_failure`() =
        runTest {
            val recorder = NotificationsApiRecorder()
            recorder.listNotificationsResponse =
                listNotificationsBody(
                    notifications =
                        listOf(
                            notificationJson(
                                actorDid = "did:plc:alice",
                                reason = "like",
                                reasonSubject = "at://did:plc:user/app.bsky.feed.post/p1",
                                uri = "at://did:plc:alice/app.bsky.feed.like/r1",
                            ),
                        ),
                )
            // getPostsBuilder remains null → recorder's MockEngine will respond 500 for getPosts.
            recorder.getPostsForceFail = true
            val repo = makeRepo(recorder, testScheduler = testScheduler)

            val result = repo.fetchPage(NotificationFilter.All, cursor = null)
            assertTrue(result.isFailure, "getPosts failure should propagate up the fetchPage chain")
        }

    // ----- Helpers --------------------------------------------------------

    private fun makeRepo(
        recorder: NotificationsApiRecorder,
        testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ): DefaultNotificationsRepository = makeRepo(engine = recorder.build(), testScheduler = testScheduler)

    private fun makeRepo(
        engine: MockEngine,
        testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ): DefaultNotificationsRepository =
        DefaultNotificationsRepository(
            xrpcClientProvider =
                FakeXrpcClientProvider {
                    XrpcClient(baseUrl = "https://example.test", httpClient = HttpClient(engine))
                },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
}

private class FakeXrpcClientProvider(
    private val factory: () -> XrpcClient,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = factory()
}

/**
 * Records each XRPC request the repository issues and serves canned
 * responses. Each endpoint has a `*Calls` list capturing what was sent;
 * each `*Response` (or `*Builder`) field controls what comes back.
 */
private class NotificationsApiRecorder {
    val listNotificationsCalls = mutableListOf<QueryParams>()
    val getPostsCalls = mutableListOf<List<String>>()
    val updateSeenCalls = mutableListOf<CapturedRequest>()
    val getUnreadCountCalls = mutableListOf<QueryParams>()

    var listNotificationsResponse: String = """{"notifications":[]}"""
    var getPostsBuilder: ((uris: List<String>) -> String)? = null
    var getPostsForceFail: Boolean = false
    var updateSeenResponse: String = "{}"
    var getUnreadCountResponse: String = """{"count":0}"""

    fun build(): MockEngine =
        MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("listNotifications") -> {
                    listNotificationsCalls += QueryParams.from(request)
                    respondJson(listNotificationsResponse)
                }
                path.endsWith("getPosts") -> {
                    val uris =
                        request.url.parameters
                            .getAll("uris")
                            .orEmpty()
                    getPostsCalls += uris
                    if (getPostsForceFail) {
                        respondError(HttpStatusCode.InternalServerError)
                    } else {
                        val builder = getPostsBuilder ?: error("getPostsBuilder not set but getPosts was called")
                        respondJson(builder(uris))
                    }
                }
                path.endsWith("updateSeen") -> {
                    updateSeenCalls += CapturedRequest(request)
                    respondJson(updateSeenResponse)
                }
                path.endsWith("getUnreadCount") -> {
                    getUnreadCountCalls += QueryParams.from(request)
                    respondJson(getUnreadCountResponse)
                }
                else -> error("unexpected request to ${request.url}")
            }
        }
}

private class QueryParams(
    private val map: Map<String, List<String>>,
) {
    fun get(key: String): String? = map[key]?.firstOrNull()

    fun getAll(key: String): List<String>? = map[key]

    companion object {
        fun from(request: HttpRequestData): QueryParams {
            val params = request.url.parameters
            val collected = mutableMapOf<String, MutableList<String>>()
            for (name in params.names()) {
                collected[name] = params.getAll(name).orEmpty().toMutableList()
            }
            return QueryParams(collected)
        }
    }
}

private class CapturedRequest(
    request: HttpRequestData,
) {
    val body: String =
        runCatching {
            val content = request.body
            when (content) {
                is io.ktor.http.content.TextContent -> content.text
                else -> content.toString()
            }
        }.getOrDefault("")
}

private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData =
    respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", "application/json"),
    )

// ----- Fixture builders ---------------------------------------------------

private fun listNotificationsBody(
    notifications: List<String>,
    cursor: String? = null,
): String {
    val cursorJson = if (cursor != null) ",\"cursor\":${quote(cursor)}" else ""
    return """{"notifications":[${notifications.joinToString(",")}]$cursorJson}"""
}

private fun notificationJson(
    actorDid: String,
    reason: String,
    reasonSubject: String?,
    uri: String,
    indexedAt: String = "2026-05-26T12:00:00Z",
    isRead: Boolean = false,
    cid: String = "bafyfake-cid",
): String {
    val reasonSubjectField = if (reasonSubject != null) ",\"reasonSubject\":${quote(reasonSubject)}" else ""
    return """
        {
            "uri":${quote(uri)},
            "cid":${quote(cid)},
            "author":{"did":${quote(actorDid)},"handle":"actor.bsky.social"},
            "reason":${quote(reason)}$reasonSubjectField,
            "record":{},
            "isRead":$isRead,
            "indexedAt":${quote(indexedAt)}
        }
        """.trimIndent()
}

private fun getPostsBody(posts: List<String>): String = """{"posts":[${posts.joinToString(",")}]}"""

private fun postViewJson(
    uri: String,
    authorDid: String,
    cid: String = "bafyfake-cid",
    text: String = "Subject post text.",
    indexedAt: String = "2026-05-25T10:00:00Z",
    createdAt: String = "2026-05-25T10:00:00Z",
): String =
    """
    {
        "uri":${quote(uri)},
        "cid":${quote(cid)},
        "author":{"did":${quote(authorDid)},"handle":"author.bsky.social"},
        "record":{
            "${'$'}type":"app.bsky.feed.post",
            "text":${quote(text)},
            "createdAt":${quote(createdAt)}
        },
        "indexedAt":${quote(indexedAt)}
    }
    """.trimIndent()

private fun quote(s: String): String = "\"${s.replace("\"", "\\\"")}\""
