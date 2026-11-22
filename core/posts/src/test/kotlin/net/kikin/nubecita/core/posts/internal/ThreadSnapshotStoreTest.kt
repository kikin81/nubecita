package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.ThreadSnapshotDao
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotItemEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

internal class ThreadSnapshotStoreTest {
    private val me = "did:plc:me"
    private val now = Instant.fromEpochMilliseconds(5_000)
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "me.test", did = me))
    private val sessionStateProvider = mockk<SessionStateProvider> { every { state } returns session }
    private val snapshotDao = mockk<ThreadSnapshotDao>(relaxed = true)
    private val postDao = mockk<CachedPostDao>(relaxed = true)
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun TestScope.store() =
        ThreadSnapshotStore(
            threadSnapshotDao = snapshotDao,
            cachedPostDao = postDao,
            sessionStateProvider = sessionStateProvider,
            clock = clock,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

    private val entries =
        listOf(
            RawThreadEntry(ThreadSnapshotKind.ANCESTOR, "at://p", depth = 0, post = post("at://p")),
            RawThreadEntry(ThreadSnapshotKind.FOCUS, "at://f", depth = 0, post = post("at://f")),
            RawThreadEntry(ThreadSnapshotKind.REPLY, "at://r", depth = 1, post = post("at://r")),
            RawThreadEntry(ThreadSnapshotKind.BLOCKED, "at://b", depth = 1, authorDid = "did:plc:blocked"),
        )

    @Test
    fun `write stores posts and snapshot, evicts beyond 50, then collects unreferenced posts`() =
        runTest {
            val posts = slot<List<CachedPostEntity>>()
            val items = slot<List<ThreadSnapshotItemEntity>>()
            coEvery { postDao.upsert(capture(posts)) } returns Unit
            coEvery { snapshotDao.replace(any(), capture(items)) } returns Unit

            store().write("at://f", entries)

            assertEquals(listOf("at://p", "at://f", "at://r"), posts.captured.map { it.uri })
            assertEquals(listOf(0, 1, 2, 3), items.captured.map { it.position })
            assertEquals("did:plc:blocked", items.captured[3].authorDid)
            coVerifyOrder {
                snapshotDao.replace(ThreadSnapshotEntity(me, "at://f", now, now), any())
                snapshotDao.evictBeyond(me, 50)
                postDao.deleteUnreferenced(me)
            }
        }

    @Test
    fun `write then read round-trips the entries`() =
        runTest {
            val posts = slot<List<CachedPostEntity>>()
            val items = slot<List<ThreadSnapshotItemEntity>>()
            coEvery { postDao.upsert(capture(posts)) } returns Unit
            coEvery { snapshotDao.replace(any(), capture(items)) } returns Unit
            store().write("at://f", entries)
            coEvery { snapshotDao.items(me, "at://f") } returns items.captured
            coEvery { postDao.byUris(me, any()) } returns posts.captured

            val read = store().read("at://f")

            assertEquals(entries.map { Triple(it.kind, it.uri, it.depth) }, read?.map { Triple(it.kind, it.uri, it.depth) })
            assertEquals(
                "at://r",
                read
                    ?.get(2)
                    ?.post
                    ?.uri
                    ?.raw,
            )
            assertEquals("did:plc:blocked", read?.get(3)?.authorDid)
            coVerify { snapshotDao.touch(me, "at://f", now) }
        }

    @Test
    fun `write then read keeps thread numbering, cut marks and the other-replies row`() =
        runTest {
            val v2Entries =
                listOf(
                    RawThreadEntry(ThreadSnapshotKind.ANCESTOR, "at://p", depth = 0, post = post("at://p"), opThreadIndex = 1, opThreadCount = 3, moreParents = true),
                    RawThreadEntry(ThreadSnapshotKind.FOCUS, "at://f", depth = 0, post = post("at://f"), opThreadIndex = 2, opThreadCount = 3),
                    RawThreadEntry(ThreadSnapshotKind.REPLY, "at://r", depth = 1, post = post("at://r"), opThreadIndex = 3, opThreadCount = 3, moreReplies = 7),
                    RawThreadEntry(ThreadSnapshotKind.OTHER_REPLIES, "at://f", depth = 0),
                )
            val posts = slot<List<CachedPostEntity>>()
            val items = slot<List<ThreadSnapshotItemEntity>>()
            coEvery { postDao.upsert(capture(posts)) } returns Unit
            coEvery { snapshotDao.replace(any(), capture(items)) } returns Unit
            store().write("at://f", v2Entries)
            coEvery { snapshotDao.items(me, "at://f") } returns items.captured
            coEvery { postDao.byUris(me, any()) } returns posts.captured

            val read = store().read("at://f")

            assertEquals(v2Entries.map { it.copy(post = null) }, read?.map { it.copy(post = null) })
            // The other-replies row has no post of its own to store or look up.
            assertEquals(listOf("at://p", "at://f", "at://r"), posts.captured.map { it.uri })
        }

    @Test
    fun `rows written before the new columns read as a plain thread`() =
        runTest {
            coEvery { snapshotDao.items(me, "at://f") } returns
                listOf(
                    ThreadSnapshotItemEntity(me, "at://f", 0, ThreadSnapshotKind.FOCUS, "at://f", 0, null),
                    ThreadSnapshotItemEntity(me, "at://f", 1, ThreadSnapshotKind.REPLY, "at://r", 1, null),
                )
            val posts = slot<List<CachedPostEntity>>()
            coEvery { postDao.upsert(capture(posts)) } returns Unit
            store().write("at://seed", entries.filter { it.post != null })
            coEvery { postDao.byUris(me, any()) } returns posts.captured

            val read = store().read("at://f")

            assertEquals(
                listOf(
                    RawThreadEntry(ThreadSnapshotKind.FOCUS, "at://f", depth = 0),
                    RawThreadEntry(ThreadSnapshotKind.REPLY, "at://r", depth = 1),
                ),
                read?.map { it.copy(post = null) },
            )
        }

    @Test
    fun `read with no snapshot is null`() =
        runTest {
            coEvery { snapshotDao.items(me, "at://f") } returns emptyList()

            assertNull(store().read("at://f"))
        }

    @Test
    fun `read whose focus body is gone from the post store is null`() =
        runTest {
            coEvery { snapshotDao.items(me, "at://f") } returns
                listOf(ThreadSnapshotItemEntity(me, "at://f", 0, ThreadSnapshotKind.FOCUS, "at://f", 0, null))
            coEvery { postDao.byUris(me, any()) } returns emptyList()

            assertNull(store().read("at://f"))
        }

    @Test
    fun `readFocusOnly returns the cached post as a lone focus`() =
        runTest {
            val posts = slot<List<CachedPostEntity>>()
            coEvery { postDao.upsert(capture(posts)) } returns Unit
            store().write("at://f", entries)
            coEvery { postDao.byUri(me, "at://f") } returns posts.captured.single { it.uri == "at://f" }

            val read = store().readFocusOnly("at://f")

            assertEquals(listOf(ThreadSnapshotKind.FOCUS), read?.map { it.kind })
            assertEquals(
                "at://f",
                read
                    ?.single()
                    ?.post
                    ?.uri
                    ?.raw,
            )
        }

    @Test
    fun `everything is a no-op when signed out`() =
        runTest {
            session.value = SessionState.SignedOut

            assertNull(store().read("at://f"))
            assertNull(store().readFocusOnly("at://f"))
            store().write("at://f", entries)
            store().remove("at://f")

            coVerify(exactly = 0) { snapshotDao.replace(any(), any()) }
            coVerify(exactly = 0) { snapshotDao.delete(any(), any()) }
        }

    @Test
    fun `remove deletes the snapshot then collects its posts`() =
        runTest {
            store().remove("at://f")

            coVerifyOrder {
                snapshotDao.delete(me, "at://f")
                postDao.deleteUnreferenced(me)
            }
        }

    private fun post(uri: String): PostView =
        PostView(
            author = ProfileViewBasic(did = Did("did:plc:author"), handle = Handle("author.test")),
            cid = Cid("bafyreitestcidvalue"),
            indexedAt = Datetime("2026-04-25T12:00:00Z"),
            record =
                buildJsonObject {
                    put("\$type", "app.bsky.feed.post")
                    put("text", "body of $uri")
                    put("createdAt", "2026-04-25T12:00:00Z")
                },
            uri = AtUri(uri),
        )
}
