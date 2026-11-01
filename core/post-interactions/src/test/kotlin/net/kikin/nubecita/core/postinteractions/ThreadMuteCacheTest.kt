package net.kikin.nubecita.core.postinteractions

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionsCache
import net.kikin.nubecita.core.postinteractions.internal.FakeBookmarkRepository
import net.kikin.nubecita.core.postinteractions.internal.FakeLikeRepostRepository
import net.kikin.nubecita.core.postinteractions.internal.FakePinnedPostRepository
import net.kikin.nubecita.core.postinteractions.internal.FakeThreadMuteRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Optimistic thread mute in [DefaultPostInteractionsCache] (nubecita-iwjz.4).
 * A thread is keyed by `threadRootUri ?: id`; a write flips every cached post
 * in the thread and holds a two-way override until a refresh agrees.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ThreadMuteCacheTest {
    private val root = "at://did:plc:a/app.bsky.feed.post/root"
    private val replyA = "at://did:plc:b/app.bsky.feed.post/a"
    private val replyB = "at://did:plc:c/app.bsky.feed.post/b"
    private val other = "at://did:plc:d/app.bsky.feed.post/other"

    @Test
    fun `mute flips every cached post in the thread before the write returns`() =
        runTest {
            val repo = FakeThreadMuteRepository().apply { gate = CompletableDeferred() }
            val cache = newCache(repo).seeded()

            val result = async { cache.toggleThreadMute(replyA) }
            runCurrent()

            assertTrue(muted(cache, root) && muted(cache, replyA) && muted(cache, replyB))
            assertFalse(muted(cache, other))
            repo.gate?.complete(Unit)
            assertEquals(Result.success(true), result.await())
        }

    @Test
    fun `mute from a reply sends the thread root`() =
        runTest {
            val repo = FakeThreadMuteRepository()
            val cache = newCache(repo).seeded()

            cache.toggleThreadMute(replyA)
            advanceUntilIdle()

            assertEquals(listOf(root), repo.muteCalls)
        }

    @Test
    fun `mute on the root post flips its replies`() =
        runTest {
            val repo = FakeThreadMuteRepository()
            val cache = newCache(repo).seeded()

            cache.toggleThreadMute(root)
            advanceUntilIdle()

            assertEquals(listOf(root), repo.muteCalls)
            assertTrue(muted(cache, replyA) && muted(cache, replyB))
        }

    @Test
    fun `failure restores every post in the thread`() =
        runTest {
            val repo = FakeThreadMuteRepository().apply { nextResult = Result.failure(RuntimeException("boom")) }
            val cache = newCache(repo).seeded()

            val result = cache.toggleThreadMute(replyA)
            advanceUntilIdle()

            assertTrue(result.isFailure)
            assertFalse(muted(cache, root) || muted(cache, replyA) || muted(cache, replyB))
            assertNull(cache.state.value[replyA]?.threadMuteOverride)
        }

    @Test
    fun `a stale refresh keeps a successful mute`() =
        runTest {
            val cache = newCache(FakeThreadMuteRepository()).seeded()
            cache.toggleThreadMute(replyA)
            advanceUntilIdle()

            cache.seed(thread(threadMuted = false))

            assertTrue(muted(cache, root) && muted(cache, replyA))
        }

    @Test
    fun `a stale refresh does not re-mute after an unmute`() =
        runTest {
            val repo = FakeThreadMuteRepository()
            val cache = newCache(repo).apply { seed(thread(threadMuted = true)) }
            cache.toggleThreadMute(replyA)
            advanceUntilIdle()
            assertEquals(listOf(root), repo.unmuteCalls)

            cache.seed(thread(threadMuted = true))

            assertFalse(muted(cache, root) || muted(cache, replyA))
        }

    @Test
    fun `wire agreement clears the override so the server wins afterwards`() =
        runTest {
            val cache = newCache(FakeThreadMuteRepository()).seeded()
            cache.toggleThreadMute(replyA)
            advanceUntilIdle()

            cache.seed(thread(threadMuted = true))
            assertNull(cache.state.value[replyA]?.threadMuteOverride)

            cache.seed(thread(threadMuted = false))
            assertFalse(muted(cache, replyA))
        }

    @Test
    fun `a repeat tap while the write is in flight is ignored`() =
        runTest {
            val repo = FakeThreadMuteRepository().apply { gate = CompletableDeferred() }
            val cache = newCache(repo).seeded()

            val first = async { cache.toggleThreadMute(replyA) }
            runCurrent()
            val second = cache.toggleThreadMute(replyB)
            repo.gate?.complete(Unit)
            first.await()

            assertEquals(1, repo.muteCalls.size + repo.unmuteCalls.size)
            assertEquals(Result.success(true), second)
            assertTrue(muted(cache, replyB))
        }

    @Test
    fun `leaving the screen mid-mute keeps the in-flight guard`() =
        runTest {
            val repo = FakeThreadMuteRepository().apply { gate = CompletableDeferred() }
            val cache = newCache(repo).seeded()

            val awaiting = launch { cache.toggleThreadMute(replyA) }
            runCurrent()
            awaiting.cancel()
            runCurrent()
            val second = async { cache.toggleThreadMute(replyB) }
            runCurrent()

            assertEquals(1, repo.muteCalls.size + repo.unmuteCalls.size)
            repo.gate?.complete(Unit)
            second.await()
        }

    @Test
    fun `an unseeded reply mutes the root the caller passes`() =
        runTest {
            val repo = FakeThreadMuteRepository()
            val cache = newCache(repo)

            cache.toggleThreadMute("at://did:plc:me/app.bsky.feed.post/justsent", threadRootUri = root)
            advanceUntilIdle()

            assertEquals(listOf(root), repo.muteCalls)
        }

    @Test
    fun `a like completing after a mute keeps the mute`() =
        runTest {
            val likes = FakeLikeRepostRepository().apply { nextDelayMs = 1_000 }
            val cache =
                DefaultPostInteractionsCache(
                    likeRepostRepository = likes,
                    bookmarkRepository = FakeBookmarkRepository(),
                    threadMuteRepository = FakeThreadMuteRepository(),
                    pinnedPostRepository = FakePinnedPostRepository(),
                    applicationScope = this,
                ).seeded()

            val like = async { cache.toggleLike(replyA, "bafy") }
            runCurrent()
            cache.toggleThreadMute(replyA)
            runCurrent()
            assertTrue(muted(cache, replyA))
            like.await()

            assertTrue(muted(cache, replyA), "the like's completion must not restore its pre-mute snapshot")
            assertEquals(true, cache.state.value[replyA]?.threadMuteOverride)
        }

    @Test
    fun `an explicit target that already holds writes nothing`() =
        runTest {
            val repo = FakeThreadMuteRepository()
            val cache = newCache(repo).apply { seed(thread(threadMuted = true)) }

            val result = cache.toggleThreadMute(replyA, target = true)
            advanceUntilIdle()

            assertEquals(Result.success(true), result)
            assertTrue(repo.muteCalls.isEmpty() && repo.unmuteCalls.isEmpty())
        }

    @Test
    fun `a rollback after the cache was cleared recreates nothing`() =
        runTest {
            val repo =
                FakeThreadMuteRepository().apply {
                    gate = CompletableDeferred()
                    nextResult = Result.failure(RuntimeException("signed out"))
                }
            val cache = newCache(repo).seeded()

            val write = async { cache.toggleThreadMute(replyA) }
            runCurrent()
            cache.clear()
            repo.gate?.complete(Unit)
            write.await()

            assertTrue(cache.state.value.isEmpty(), "rollback must not resurrect cleared entries: ${cache.state.value.keys}")
        }

    @Test
    fun `merge projects the thread-muted flag onto the post`() =
        runTest {
            val cache = newCache(FakeThreadMuteRepository()).seeded()
            cache.toggleThreadMute(replyA)
            advanceUntilIdle()

            val merged = post(replyA, root).mergeInteractionState(requireNotNull(cache.state.value[replyA]))

            assertTrue(merged.viewer.isThreadMuted)
        }

    private fun muted(
        cache: DefaultPostInteractionsCache,
        uri: String,
    ): Boolean = cache.state.value[uri]?.isThreadMuted == true

    private fun DefaultPostInteractionsCache.seeded(): DefaultPostInteractionsCache = apply { seed(thread(threadMuted = false)) }

    private fun thread(threadMuted: Boolean): List<PostUi> =
        listOf(
            post(root, rootUri = null, threadMuted = threadMuted),
            post(replyA, root, threadMuted),
            post(replyB, root, threadMuted),
            post(other, rootUri = null, threadMuted = false),
        )

    private fun TestScope.newCache(repo: FakeThreadMuteRepository): DefaultPostInteractionsCache =
        DefaultPostInteractionsCache(
            likeRepostRepository = FakeLikeRepostRepository(),
            bookmarkRepository = FakeBookmarkRepository(),
            threadMuteRepository = repo,
            pinnedPostRepository = FakePinnedPostRepository(),
            applicationScope = this,
        )

    private fun post(
        id: String,
        rootUri: String?,
        threadMuted: Boolean = false,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafy",
            author = AuthorUi(did = "did:plc:x", handle = "x.bsky.social", displayName = "X", avatarUrl = null),
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            text = "",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(isThreadMuted = threadMuted),
            repostedBy = null,
            threadRootUri = rootUri,
        )
}
