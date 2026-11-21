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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
    fun `cachedPostThread with nothing cached is null`() =
        runTest {
            coEvery { store.read(FOCUS) } returns null
            coEvery { store.readFocusOnly(FOCUS) } returns null

            assertNull(repo(MockEngine { error("no network") }).cachedPostThread(FOCUS))
        }

    // ---------- harness ----------

    private suspend fun DefaultPostThreadRepository.rawFocusEntries(): List<RawThreadEntry> {
        // Capture what a successful fetch hands the store.
        var captured: List<RawThreadEntry> = emptyList()
        coEvery { store.write(FOCUS, any()) } answers { captured = secondArg() }
        getPostThread(FOCUS)
        return captured
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.ok(body: String) = respond(ByteReadChannel(body), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))

    private fun kotlinx.coroutines.test.TestScope.repo(engine: MockEngine) =
        DefaultPostThreadRepository(
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
        val THREAD_JSON =
            """
            {"thread":{"${'$'}type":"app.bsky.feed.defs#threadViewPost","post":{
              "uri":"$FOCUS","cid":"bafyreitestcidvalue","indexedAt":"2026-04-25T12:00:00Z",
              "author":{"did":"did:plc:author","handle":"author.test"},
              "record":{"${'$'}type":"app.bsky.feed.post","text":"hello","createdAt":"2026-04-25T12:00:00Z"}}}}
            """.trimIndent()
    }
}
