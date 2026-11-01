package net.kikin.nubecita.core.postinteractions

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
 * Optimistic pin / unpin in [DefaultPostInteractionsCache] (nubecita-iwjz.2).
 * One profile record holds the pin, so pinning B unpins A, and only one pin
 * write runs at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PinCacheTest {
    private val postA = "at://did:plc:me/app.bsky.feed.post/a"
    private val postB = "at://did:plc:me/app.bsky.feed.post/b"

    @Test
    fun `pinning B moves the pin from A before the write returns`() =
        runTest {
            val repo = FakePinnedPostRepository().apply { gate = CompletableDeferred() }
            val cache = newCache(repo).apply { seed(posts(aPinned = true)) }

            val result = async { cache.togglePin(postB, "bafb") }
            runCurrent()

            assertTrue(pinned(cache, postB))
            assertFalse(pinned(cache, postA))
            repo.gate?.complete(Unit)
            assertEquals(Result.success(true), result.await())
            assertEquals(listOf(postB to "bafb"), repo.pinCalls)
        }

    @Test
    fun `a failed pin restores both posts`() =
        runTest {
            val repo = FakePinnedPostRepository().apply { nextResult = Result.failure(RuntimeException("boom")) }
            val cache = newCache(repo).apply { seed(posts(aPinned = true)) }

            val result = cache.togglePin(postB, "bafb")
            advanceUntilIdle()

            assertTrue(result.isFailure)
            assertTrue(pinned(cache, postA))
            assertFalse(pinned(cache, postB))
            assertNull(cache.state.value[postB]?.pinOverride)
        }

    @Test
    fun `a stale refresh keeps the moved pin`() =
        runTest {
            val cache = newCache(FakePinnedPostRepository()).apply { seed(posts(aPinned = true)) }
            cache.togglePin(postB, "bafb")
            advanceUntilIdle()

            cache.seed(posts(aPinned = true))

            assertTrue(pinned(cache, postB))
            assertFalse(pinned(cache, postA))
        }

    @Test
    fun `a second pin while the first is in flight is ignored`() =
        runTest {
            val repo = FakePinnedPostRepository().apply { gate = CompletableDeferred() }
            val cache = newCache(repo).apply { seed(posts(aPinned = false)) }

            val first = async { cache.togglePin(postB, "bafb") }
            runCurrent()
            val second = cache.togglePin(postA, "bafa")
            repo.gate?.complete(Unit)
            first.await()

            assertEquals(Result.success(null), second)
            assertEquals(1, repo.pinCalls.size + repo.unpinCalls.size)
            assertFalse(pinned(cache, postA))
        }

    @Test
    fun `unpin clears only this post`() =
        runTest {
            val repo = FakePinnedPostRepository()
            val cache = newCache(repo).apply { seed(posts(aPinned = true)) }

            val result = cache.togglePin(postA, "bafa")
            advanceUntilIdle()

            assertEquals(Result.success(false), result)
            assertEquals(listOf(postA), repo.unpinCalls)
            assertFalse(pinned(cache, postA) || pinned(cache, postB))
        }

    @Test
    fun `merge projects the pinned flag onto the post`() =
        runTest {
            val cache = newCache(FakePinnedPostRepository()).apply { seed(posts(aPinned = false)) }
            cache.togglePin(postB, "bafb")
            advanceUntilIdle()

            val merged = post(postB, pinned = false).mergeInteractionState(requireNotNull(cache.state.value[postB]))

            assertTrue(merged.viewer.isPinned)
        }

    private fun pinned(
        cache: DefaultPostInteractionsCache,
        uri: String,
    ): Boolean = cache.state.value[uri]?.isPinned == true

    private fun posts(aPinned: Boolean): List<PostUi> = listOf(post(postA, pinned = aPinned), post(postB, pinned = false))

    private fun TestScope.newCache(repo: FakePinnedPostRepository): DefaultPostInteractionsCache =
        DefaultPostInteractionsCache(
            likeRepostRepository = FakeLikeRepostRepository(),
            bookmarkRepository = FakeBookmarkRepository(),
            threadMuteRepository = FakeThreadMuteRepository(),
            pinnedPostRepository = repo,
            applicationScope = this,
        )

    private fun post(
        id: String,
        pinned: Boolean,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafy",
            author = AuthorUi(did = "did:plc:me", handle = "me.bsky.social", displayName = "Me", avatarUrl = null),
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            text = "",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(isOwnPost = true, isPinned = pinned),
            repostedBy = null,
        )
}
