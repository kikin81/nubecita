package net.kikin.nubecita.core.postinteractions

import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionsCache
import net.kikin.nubecita.core.postinteractions.internal.FakeLikeRepostRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultPostInteractionsCacheTest {
    private companion object {
        private const val PENDING_LIKE_SENTINEL_FOR_TEST = "at://pending:optimistic"
    }

    @Test
    fun `toggleLike from empty cache emits optimistic then success and calls like`() =
        runTest {
            val fake =
                FakeLikeRepostRepository().apply {
                    nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/abc"))
                }
            val cache = newCache(fake)

            val result = cache.toggleLike(postUri = "at://did:plc:author/app.bsky.feed.post/post-1", postCid = "bafy123")
            advanceUntilIdle()

            assertTrue(result.isSuccess, "toggleLike from empty cache MUST succeed")
            val state = cache.state.value["at://did:plc:author/app.bsky.feed.post/post-1"]
            assertEquals(
                "at://did:plc:viewer/app.bsky.feed.like/abc",
                state?.viewerLikeUri,
                "viewerLikeUri MUST hold the wire-returned AtUri after success",
            )
            assertEquals(1L, state?.likeCount, "likeCount MUST be incremented from 0 to 1")
            assertEquals(
                PendingState.None,
                state?.pendingLikeWrite,
                "pendingLikeWrite MUST clear on success",
            )
            assertEquals(1, fake.likeCalls.get(), "like() MUST be called exactly once")
            assertEquals(0, fake.unlikeCalls.get(), "unlike() MUST NOT be called")
        }

    @Test
    fun `toggleLike from seeded not-liked state increments count and calls like`() =
        runTest {
            val fake =
                FakeLikeRepostRepository().apply {
                    nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/abc"))
                }
            val cache = newCache(fake)
            cache.seedDirectly("at://post-2", PostInteractionState(viewerLikeUri = null, likeCount = 10))

            val result = cache.toggleLike("at://post-2", "bafy222")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-2"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.like/abc", state?.viewerLikeUri)
            assertEquals(11L, state?.likeCount, "count 10 → 11")
        }

    @Test
    fun `toggleLike from seeded liked state decrements count and calls unlike`() =
        runTest {
            val fake =
                FakeLikeRepostRepository().apply {
                    nextUnlikeResult = Result.success(Unit)
                }
            val cache = newCache(fake)
            cache.seedDirectly(
                "at://post-3",
                PostInteractionState(viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/old", likeCount = 11),
            )

            val result = cache.toggleLike("at://post-3", "bafy333")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-3"]
            assertNull(state?.viewerLikeUri, "unlike clears viewerLikeUri")
            assertEquals(10L, state?.likeCount, "count 11 → 10")
            assertEquals(1, fake.unlikeCalls.get())
            assertEquals(AtUri("at://did:plc:viewer/app.bsky.feed.like/old"), fake.lastUnlikedUri)
            assertEquals(0, fake.likeCalls.get(), "like() MUST NOT be called on unlike path")
        }

    @Test
    fun `toggleLike rolls back state and returns failure on network error`() =
        runTest {
            val networkFailure = IllegalStateException("net down")
            val fake =
                FakeLikeRepostRepository().apply {
                    nextLikeResult = Result.failure(networkFailure)
                }
            val cache = newCache(fake)
            val initial = PostInteractionState(viewerLikeUri = null, likeCount = 7)
            cache.seedDirectly("at://post-fail", initial)

            val result = cache.toggleLike("at://post-fail", "bafyFAIL")
            advanceUntilIdle()

            assertTrue(result.isFailure, "toggleLike MUST surface the underlying failure")
            assertEquals(networkFailure, result.exceptionOrNull())

            val state = cache.state.value["at://post-fail"]
            assertEquals(
                initial.viewerLikeUri,
                state?.viewerLikeUri,
                "rollback MUST restore pre-tap viewerLikeUri (null)",
            )
            assertEquals(
                initial.likeCount,
                state?.likeCount,
                "rollback MUST restore pre-tap likeCount (7)",
            )
            assertEquals(
                PendingState.None,
                state?.pendingLikeWrite,
                "rollback MUST clear pendingLikeWrite",
            )
        }

    @Test
    fun `toggleLike is single-flight per postUri — double-tap is absorbed`() =
        runTest {
            val fake =
                FakeLikeRepostRepository().apply {
                    nextDelayMs = 1_000 // hold the in-flight call so a second arrives mid-flight
                    nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/x"))
                }
            val cache = newCache(fake)

            // Fire two like calls back-to-back without awaiting the first.
            val first = async { cache.toggleLike("at://post-sf", "bafySF") }
            val second = async { cache.toggleLike("at://post-sf", "bafySF") }
            advanceUntilIdle()

            val firstResult = first.await()
            val secondResult = second.await()

            assertTrue(firstResult.isSuccess)
            assertTrue(secondResult.isSuccess, "second toggle MUST return synthetic success (no error)")
            assertEquals(
                1,
                fake.likeCalls.get(),
                "single-flight: like() MUST be called exactly once for the same postUri",
            )
            assertEquals(
                0,
                fake.unlikeCalls.get(),
                "single-flight: unlike() MUST NOT be called — second tap must be absorbed, not inverted",
            )
        }

    @Test
    fun `seed on empty cache writes wire data for every post`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            val post =
                samplePost(
                    id = "at://post-seed-1",
                    viewerLikeUri = null,
                    viewerRepostUri = null,
                    likeCount = 5,
                    repostCount = 2,
                )

            cache.seed(listOf(post))

            val state = cache.state.value["at://post-seed-1"]
            assertEquals(null, state?.viewerLikeUri)
            assertEquals(null, state?.viewerRepostUri)
            assertEquals(5L, state?.likeCount)
            assertEquals(2L, state?.repostCount)
            assertEquals(PendingState.None, state?.pendingLikeWrite)
        }

    @Test
    fun `seed preserves in-flight optimistic state against stale wire data`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            val pendingState =
                PostInteractionState(
                    viewerLikeUri = "at://pending:optimistic",
                    likeCount = 6,
                    pendingLikeWrite = PendingState.Pending,
                )
            cache.seedDirectly("at://post-pending", pendingState)

            // Wire data shows the like as not-yet-indexed (stale because the
            // appview hasn't caught up to the user's recent createRecord).
            val stalePost =
                samplePost(
                    id = "at://post-pending",
                    viewerLikeUri = null,
                    likeCount = 5,
                )
            cache.seed(listOf(stalePost))

            val state = cache.state.value["at://post-pending"]
            assertEquals(
                pendingState,
                state,
                "seed MUST preserve in-flight optimistic state entirely while pending",
            )
        }

    @Test
    fun `seed reseeds from wire when no write is pending and wire is fresh`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            val existing = PostInteractionState(viewerLikeUri = null, likeCount = 5)
            cache.seedDirectly("at://post-reseed", existing)

            // Wire returns updated counts and a fresh-from-server like AtUri
            // (someone else may have liked between fetches).
            val freshPost =
                samplePost(
                    id = "at://post-reseed",
                    viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/fresh",
                    likeCount = 8,
                )
            cache.seed(listOf(freshPost))

            val state = cache.state.value["at://post-reseed"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.like/fresh", state?.viewerLikeUri)
            assertEquals(8L, state?.likeCount)
        }

    @Test
    fun `seed during in-flight toggleLike preserves optimistic state then promotes on success`() =
        runTest {
            val fake =
                FakeLikeRepostRepository().apply {
                    nextDelayMs = 500 // hold the like in flight
                    nextLikeResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/promoted"))
                }
            val cache = newCache(fake)
            cache.seedDirectly("at://post-refresh", PostInteractionState(viewerLikeUri = null, likeCount = 3))

            // Fire toggleLike; it suspends inside the fake.
            val toggle = async { cache.toggleLike("at://post-refresh", "bafyRF") }
            // Run until the optimistic emission has landed but the fake is still suspended.
            runCurrent()

            // Now simulate a refresh: wire returns stale data (no like, count 3).
            val stale =
                samplePost(
                    id = "at://post-refresh",
                    viewerLikeUri = null,
                    likeCount = 3,
                )
            cache.seed(listOf(stale))

            val midFlight = cache.state.value["at://post-refresh"]
            assertEquals(
                PENDING_LIKE_SENTINEL_FOR_TEST,
                midFlight?.viewerLikeUri,
                "seed during in-flight MUST preserve the optimistic sentinel",
            )
            assertEquals(
                4L,
                midFlight?.likeCount,
                "seed during in-flight MUST preserve the optimistic count delta",
            )
            assertEquals(PendingState.Pending, midFlight?.pendingLikeWrite)

            // Now let the fake complete.
            advanceUntilIdle()
            val finalResult = toggle.await()
            assertTrue(finalResult.isSuccess)
            val final = cache.state.value["at://post-refresh"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.like/promoted", final?.viewerLikeUri)
            assertEquals(4L, final?.likeCount)
            assertEquals(PendingState.None, final?.pendingLikeWrite)
        }

    @Test
    fun `clear empties the cache state`() =
        runTest {
            val cache = newCache(FakeLikeRepostRepository())
            cache.seedDirectly("at://post-a", PostInteractionState(viewerLikeUri = null, likeCount = 1))
            cache.seedDirectly("at://post-b", PostInteractionState(viewerLikeUri = "at://like/x", likeCount = 9))
            assertEquals(2, cache.state.value.size)

            cache.clear()

            assertTrue(cache.state.value.isEmpty(), "clear MUST empty the cache state")
        }

    @Test
    fun `toggleRepost from seeded not-reposted state increments count and calls repost`() =
        runTest {
            val fake =
                FakeLikeRepostRepository().apply {
                    nextRepostResult = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.repost/r1"))
                }
            val cache = newCache(fake)
            cache.seedDirectly("at://post-rp", PostInteractionState(viewerRepostUri = null, repostCount = 2))

            val result = cache.toggleRepost("at://post-rp", "bafyRP")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-rp"]
            assertEquals("at://did:plc:viewer/app.bsky.feed.repost/r1", state?.viewerRepostUri)
            assertEquals(3L, state?.repostCount)
            assertEquals(1, fake.repostCalls.get())
        }

    @Test
    fun `toggleRepost from seeded reposted state decrements count and calls unrepost`() =
        runTest {
            val fake = FakeLikeRepostRepository()
            val cache = newCache(fake)
            cache.seedDirectly(
                "at://post-unrp",
                PostInteractionState(viewerRepostUri = "at://did:plc:viewer/app.bsky.feed.repost/old", repostCount = 3),
            )

            val result = cache.toggleRepost("at://post-unrp", "bafyUNRP")
            advanceUntilIdle()

            assertTrue(result.isSuccess)
            val state = cache.state.value["at://post-unrp"]
            assertNull(state?.viewerRepostUri)
            assertEquals(2L, state?.repostCount)
            assertEquals(1, fake.unrepostCalls.get())
        }

    @Test
    fun `toggleRepost rolls back state and returns failure on network error`() =
        runTest {
            val networkFailure = IllegalStateException("net down")
            val fake =
                FakeLikeRepostRepository().apply {
                    nextRepostResult = Result.failure(networkFailure)
                }
            val cache = newCache(fake)
            val initial = PostInteractionState(viewerRepostUri = null, repostCount = 4)
            cache.seedDirectly("at://post-rp-fail", initial)

            val result = cache.toggleRepost("at://post-rp-fail", "bafyRPFAIL")
            advanceUntilIdle()

            assertTrue(result.isFailure)
            assertEquals(networkFailure, result.exceptionOrNull())
            val state = cache.state.value["at://post-rp-fail"]
            assertEquals(initial.viewerRepostUri, state?.viewerRepostUri)
            assertEquals(initial.repostCount, state?.repostCount)
            assertEquals(PendingState.None, state?.pendingRepostWrite)
        }

    // -- Test helpers ---------------------------------------------------------

    private fun TestScope.newCache(fake: FakeLikeRepostRepository): DefaultPostInteractionsCache =
        DefaultPostInteractionsCache(
            likeRepostRepository = fake,
            applicationScope = this,
        )

    /**
     * Direct injection helper for tests that need pre-existing state without
     * going through `seed(posts: List<PostUi>)`. Uses the [DefaultPostInteractionsCache.putForTest]
     * `@TestOnly` seam for type-safety across refactors — no reflection.
     */
    private fun DefaultPostInteractionsCache.seedDirectly(
        postUri: String,
        state: PostInteractionState,
    ) {
        putForTest(postUri, state)
    }

    private fun samplePost(
        id: String = "at://did:plc:author/app.bsky.feed.post/p1",
        viewerLikeUri: String? = null,
        viewerRepostUri: String? = null,
        likeCount: Int = 0,
        repostCount: Int = 0,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifake",
            author =
                AuthorUi(
                    did = "did:plc:author",
                    handle = "author.bsky.social",
                    displayName = "Author",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            text = "",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(likeCount = likeCount, repostCount = repostCount),
            viewer =
                ViewerStateUi(
                    likeUri = viewerLikeUri,
                    repostUri = viewerRepostUri,
                ),
            repostedBy = null,
        )
}
