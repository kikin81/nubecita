package net.kikin.nubecita.core.postinteractions

import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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

    // -- Test helpers ---------------------------------------------------------

    private fun TestScope.newCache(fake: FakeLikeRepostRepository): DefaultPostInteractionsCache =
        DefaultPostInteractionsCache(
            likeRepostRepository = fake,
            applicationScope = this,
        )

    /**
     * Direct injection helper for tests that need pre-existing state without
     * going through `seed(posts: List<PostUi>)`. Reaches into the private
     * `_state` MutableStateFlow via reflection.
     */
    private fun DefaultPostInteractionsCache.seedDirectly(
        postUri: String,
        state: PostInteractionState,
    ) {
        val field = DefaultPostInteractionsCache::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as kotlinx.coroutines.flow.MutableStateFlow<kotlinx.collections.immutable.PersistentMap<String, PostInteractionState>>
        flow.value = flow.value.put(postUri, state)
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
