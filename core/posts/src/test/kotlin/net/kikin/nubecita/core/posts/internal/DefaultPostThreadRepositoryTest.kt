package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ThreadPositionUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import timber.log.Timber
import java.io.IOException

/**
 * `getPostThread` writes every readable thread through to the snapshot store
 * and drops the snapshot only when the post is definitively gone;
 * `cachedPostThread` serves the snapshot (or the cached focus alone) mapped
 * with current prefs.
 */
internal class DefaultPostThreadRepositoryTest {
    private val store = mockk<ThreadSnapshotStore>(relaxed = true)

    @Test
    fun `getPostThread success writes the snapshot`() =
        runTest {
            val repo = repo(MockEngine { ok(THREAD_JSON) })

            assertTrue(repo.getPostThread(FOCUS).isSuccess)

            coVerify { store.write(FOCUS, match { entries -> entries.single().kind == ThreadSnapshotKind.FOCUS }) }
        }

    @Test
    fun `a snapshot write failure does not fail the fetch`() =
        runTest {
            coEvery { store.write(any(), any()) } throws IllegalStateException("disk full")
            val repo = repo(MockEngine { ok(THREAD_JSON) })

            assertTrue(repo.getPostThread(FOCUS).isSuccess, "the cache is a speed layer; its failure must not fail the read")
        }

    @Test
    fun `getPostThread NotFound removes the snapshot`() =
        runTest {
            val repo =
                repo(
                    MockEngine {
                        respond(
                            ByteReadChannel("""{"error":"NotFound","message":"Post not found: $FOCUS"}"""),
                            HttpStatusCode.BadRequest,
                            headersOf("Content-Type", "application/json"),
                        )
                    },
                )

            assertTrue(repo.getPostThread(FOCUS).isFailure)

            coVerify { store.remove(FOCUS) }
        }

    @Test
    fun `getPostThread of a thread whose focus is now notFound removes the snapshot`() =
        runTest {
            val repo = repo(MockEngine { ok("""{"thread":{"${'$'}type":"app.bsky.feed.defs#notFoundPost","uri":"$FOCUS","notFound":true}}""") })

            assertTrue(repo.getPostThread(FOCUS).isSuccess)

            coVerify { store.remove(FOCUS) }
            coVerify(exactly = 0) { store.write(any(), any()) }
        }

    @Test
    fun `getPostThread network failure keeps the snapshot`() =
        runTest {
            val repo = repo(MockEngine { throw IOException("offline") })

            assertTrue(repo.getPostThread(FOCUS).isFailure)

            coVerify(exactly = 0) { store.remove(any()) }
        }

    @Test
    fun `cachedPostThread maps the snapshot`() =
        runTest {
            coEvery { store.read(FOCUS) } returns repo(MockEngine { ok(THREAD_JSON) }).rawFocusEntries()

            val items = repo(MockEngine { error("no network") }).cachedPostThread(FOCUS)

            assertTrue(items?.single() is ThreadItem.Focus)
        }

    @Test
    fun `cachedPostThread falls back to the cached focus alone`() =
        runTest {
            coEvery { store.read(FOCUS) } returns null
            coEvery { store.readFocusOnly(FOCUS) } returns repo(MockEngine { ok(THREAD_JSON) }).rawFocusEntries()

            val items = repo(MockEngine { error("no network") }).cachedPostThread(FOCUS)

            assertEquals(1, items?.size)
        }

    @Test
    fun `a snapshot read failure reads as a miss, not a crash`() =
        runTest {
            coEvery { store.read(FOCUS) } throws IllegalStateException("disk I/O")

            assertNull(repo(MockEngine { error("no network") }).cachedPostThread(FOCUS))
        }

    @Test
    fun `cachedPostThread with nothing cached is null`() =
        runTest {
            coEvery { store.read(FOCUS) } returns null
            coEvery { store.readFocusOnly(FOCUS) } returns null

            assertNull(repo(MockEngine { error("no network") }).cachedPostThread(FOCUS))
        }

    // ---------- the ordered (V2) read ----------

    @Test
    fun `getOrderedThread asks V2 with the linear-view parameters`() =
        runTest {
            var asked: io.ktor.http.Url? = null
            val repo =
                repo(
                    MockEngine { request ->
                        asked = request.url
                        ok(v2Json(v2Post(0, FOCUS, index = 1, count = 2)))
                    },
                )

            assertTrue(repo.getOrderedThread(FOCUS).isSuccess)

            assertTrue(asked?.encodedPath?.endsWith("/app.bsky.unspecced.getPostThreadV2") == true, "asked ${asked?.encodedPath}")
            assertEquals(FOCUS, asked?.parameters?.get("anchor"))
            assertEquals("true", asked?.parameters?.get("above"))
            assertEquals("10", asked?.parameters?.get("below"))
            assertEquals("1", asked?.parameters?.get("branchingFactor"))
            assertEquals("top", asked?.parameters?.get("sort"))
        }

    @Test
    fun `getOrderedThread returns the numbered thread and writes it through to the snapshot`() =
        runTest {
            val written = slot<List<RawThreadEntry>>()
            coEvery { store.write(FOCUS, capture(written)) } returns Unit
            val repo = repo(MockEngine { ok(v2Json(v2Post(0, FOCUS, index = 1, count = 2), v2Post(1, REPLY, index = 2, count = 2))) })

            val items = repo.getOrderedThread(FOCUS).getOrThrow()

            assertEquals(ThreadPositionUi(index = 1, count = 2), (items[0] as ThreadItem.Focus).post.threadPosition)
            assertEquals(ThreadPositionUi(index = 2, count = 2), (items[1] as ThreadItem.Reply).post.threadPosition)
            assertEquals(listOf(1 to 2, 2 to 2), written.captured.map { it.opThreadIndex to it.opThreadCount })
        }

    @Test
    fun `a V2 response this app cannot read falls back to the v1 read and says so`() =
        runTest {
            val logs = mutableListOf<String>()
            val tree =
                object : Timber.Tree() {
                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        if (priority >= android.util.Log.WARN) logs += message
                    }
                }
            Timber.plant(tree)
            try {
                val repo = repo(MockEngine { request -> if (request.isV2()) ok("""{"thread":"not a list","hasOtherReplies":false}""") else ok(THREAD_JSON) })

                val items = repo.getOrderedThread(FOCUS).getOrThrow()

                assertTrue(items.single() is ThreadItem.Focus)
                assertTrue(logs.any { "getPostThreadV2" in it && "falling back" in it }, "logged: $logs")
            } finally {
                Timber.uproot(tree)
            }
        }

    @Test
    fun `a V2 response whose post items have drifted falls back to the v1 read and says so`() =
        runTest {
            // The shape the endpoint's "will change without notice" warning is
            // about: the envelope still decodes, the item inside it does not.
            val logs = mutableListOf<String>()
            val tree =
                object : Timber.Tree() {
                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        if (priority >= android.util.Log.WARN) logs += message
                    }
                }
            Timber.plant(tree)
            try {
                val drifted = v2Json(v2Post(0, FOCUS).replace("\"moreReplies\":0,", ""))
                val repo = repo(MockEngine { request -> if (request.isV2()) ok(drifted) else ok(THREAD_JSON) })

                val items = repo.getOrderedThread(FOCUS).getOrThrow()

                assertTrue(items.single() is ThreadItem.Focus)
                assertTrue(logs.any { "getPostThreadV2" in it && "falling back" in it }, "logged: $logs")
                coVerify(exactly = 0) { store.remove(any()) }
            } finally {
                Timber.uproot(tree)
            }
        }

    @Test
    fun `the first unusable V2 read is reported as an error and the ones after it as warnings`() =
        runTest {
            // An error becomes a Crashlytics non-fatal; a warning is a breadcrumb.
            // While V2 is down every thread open falls back, and one non-fatal per
            // process says so without spending the session's quota on repeats.
            val priorities = mutableListOf<Int>()
            val tree =
                object : Timber.Tree() {
                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        if ("falling back" in message) priorities += priority
                    }
                }
            Timber.plant(tree)
            try {
                val health = OrderedReadHealth()
                val engine = MockEngine { request -> if (request.isV2()) ok("""{"thread":"not a list","hasOtherReplies":false}""") else ok(THREAD_JSON) }

                // Two repository instances, as two screens would have: the repository is unscoped.
                assertTrue(repo(engine, health).getOrderedThread(FOCUS).isSuccess)
                assertTrue(repo(engine, health).getOrderedThread(FOCUS).isSuccess)

                assertEquals(listOf(android.util.Log.ERROR, android.util.Log.WARN), priorities)
            } finally {
                Timber.uproot(tree)
            }
        }

    @Test
    fun `a server that does not know V2 falls back to the v1 read`() =
        runTest {
            val repo =
                repo(
                    MockEngine { request ->
                        if (request.isV2()) {
                            respond(
                                ByteReadChannel("""{"error":"MethodNotImplemented","message":"Method Not Implemented"}"""),
                                HttpStatusCode.NotImplemented,
                                headersOf("Content-Type", "application/json"),
                            )
                        } else {
                            ok(THREAD_JSON)
                        }
                    },
                )

            assertTrue(repo.getOrderedThread(FOCUS).getOrThrow().single() is ThreadItem.Focus)
        }

    @Test
    fun `a connectivity failure does not fall back`() =
        runTest {
            val paths = mutableListOf<String>()
            val repo =
                repo(
                    MockEngine { request ->
                        paths += request.url.encodedPath
                        throw IOException("offline")
                    },
                )

            assertTrue(repo.getOrderedThread(FOCUS).isFailure)

            assertEquals(1, paths.size, "asked: $paths")
            coVerify(exactly = 0) { store.remove(any()) }
        }

    @Test
    fun `a post that is gone does not fall back and drops its snapshot`() =
        runTest {
            val paths = mutableListOf<String>()
            val repo =
                repo(
                    MockEngine { request ->
                        paths += request.url.encodedPath
                        respond(
                            ByteReadChannel("""{"error":"NotFound","message":"Post not found: $FOCUS"}"""),
                            HttpStatusCode.BadRequest,
                            headersOf("Content-Type", "application/json"),
                        )
                    },
                )

            assertTrue(repo.getOrderedThread(FOCUS).isFailure)

            assertEquals(1, paths.size, "asked: $paths")
            coVerify { store.remove(FOCUS) }
        }

    @Test
    fun `a V2 thread whose anchor is gone removes the snapshot`() =
        runTest {
            val repo =
                repo(MockEngine { ok("""{"thread":[{"uri":"$FOCUS","depth":0,"value":{"${'$'}type":"app.bsky.unspecced.defs#threadItemNotFound"}}],"hasOtherReplies":false}""") })

            assertTrue(repo.getOrderedThread(FOCUS).isSuccess)

            coVerify { store.remove(FOCUS) }
            coVerify(exactly = 0) { store.write(any(), any()) }
        }

    @Test
    fun `getOtherReplies maps the left-out posts to replies and caches nothing`() =
        runTest {
            var asked: io.ktor.http.Url? = null
            val repo =
                repo(
                    MockEngine { request ->
                        asked = request.url
                        ok("""{"thread":[${v2Post(1, REPLY)}]}""")
                    },
                )

            val items = repo.getOtherReplies(FOCUS).getOrThrow()

            assertTrue(asked?.encodedPath?.endsWith("/app.bsky.unspecced.getPostThreadOtherV2") == true, "asked ${asked?.encodedPath}")
            assertEquals(FOCUS, asked?.parameters?.get("anchor"))
            assertEquals(REPLY, (items.single() as ThreadItem.Reply).post.id)
            coVerify(exactly = 0) { store.write(any(), any()) }
        }

    private fun io.ktor.client.request.HttpRequestData.isV2(): Boolean = url.encodedPath.endsWith("/app.bsky.unspecced.getPostThreadV2")

    private fun v2Json(vararg items: String) = """{"thread":[${items.joinToString(",")}],"hasOtherReplies":false}"""

    private fun v2Post(
        depth: Int,
        uri: String,
        index: Int? = null,
        count: Int? = null,
    ) = """
        {"uri":"$uri","depth":$depth,"value":{"${'$'}type":"app.bsky.unspecced.defs#threadItemPost",
          "post":{"uri":"$uri","cid":"bafyreitestcidvalue","indexedAt":"2026-04-25T12:00:00Z",
            "author":{"did":"did:plc:author","handle":"author.test"},
            "record":{"${'$'}type":"app.bsky.feed.post","text":"hello","createdAt":"2026-04-25T12:00:00Z"}},
          "moreParents":false,"moreReplies":0,"opThread":${index != null},
          ${if (index != null) "\"opThreadPostIndex\":$index,\"opThreadPostCount\":$count," else ""}
          "hiddenByThreadgate":false,"mutedByViewer":false}}
        """.trimIndent()

    // ---------- harness ----------

    private suspend fun DefaultPostThreadRepository.rawFocusEntries(): List<RawThreadEntry> {
        // Capture what a successful fetch hands the store.
        var captured: List<RawThreadEntry> = emptyList()
        coEvery { store.write(FOCUS, any()) } answers { captured = secondArg() }
        getPostThread(FOCUS)
        return captured
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.ok(body: String) = respond(ByteReadChannel(body), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))

    private fun kotlinx.coroutines.test.TestScope.repo(
        engine: MockEngine,
        health: OrderedReadHealth = OrderedReadHealth(),
    ) = DefaultPostThreadRepository(
        orderedReadHealth = health,
        xrpcClientProvider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = XrpcClient(baseUrl = "https://example.test", httpClient = HttpClient(engine))
            },
        moderationPreferences =
            object : ModerationPreferencesRepository {
                override val prefs = MutableStateFlow(ModerationPrefs.DEFAULT)

                override suspend fun refresh() = Unit

                override fun resetToDefault() = Unit

                override suspend fun setAdultContentEnabled(enabled: Boolean) = Unit

                override suspend fun setVisibility(
                    label: ContentLabel,
                    visibility: LabelVisibility,
                ) = Unit
            },
        sessionStateProvider =
            object : SessionStateProvider {
                override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.SignedIn(handle = "me.test", did = "did:plc:me"))

                override suspend fun refresh() = Unit
            },
        snapshotStore = store,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private companion object {
        const val FOCUS = "at://did:plc:author/app.bsky.feed.post/focus"
        const val REPLY = "at://did:plc:author/app.bsky.feed.post/reply"
        val THREAD_JSON =
            """
            {"thread":{"${'$'}type":"app.bsky.feed.defs#threadViewPost","post":{
              "uri":"$FOCUS","cid":"bafyreitestcidvalue","indexedAt":"2026-04-25T12:00:00Z",
              "author":{"did":"did:plc:author","handle":"author.test"},
              "record":{"${'$'}type":"app.bsky.feed.post","text":"hello","createdAt":"2026-04-25T12:00:00Z"}}}}
            """.trimIndent()
    }
}
