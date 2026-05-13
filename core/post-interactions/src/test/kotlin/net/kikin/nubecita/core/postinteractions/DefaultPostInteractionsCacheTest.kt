package net.kikin.nubecita.core.postinteractions

import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionsCache
import net.kikin.nubecita.core.postinteractions.internal.FakeLikeRepostRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
