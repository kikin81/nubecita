package net.kikin.nubecita.core.postinteractions.ui.comments

import app.cash.turbine.test
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VideoCommentsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial state is Hidden and Idle`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            val state = viewModel.uiState.value
            assertNull(state.postUri)
            assertEquals(VideoCommentsDetent.Hidden, state.detent)
            assertEquals(VideoCommentsStatus.Idle, state.status)
            assertTrue(state.comments.isEmpty())
            assertFalse(state.isVisible)
        }

    @Test
    fun `OpenComments with replies projects ThreadItem Reply into comments and seeds cache`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            val focusPost = samplePost("at://focus", text = "Video Post")
            val reply1 = samplePost("at://reply1", text = "First reply!", likeCount = 5)
            val reply2 = samplePost("at://reply2", text = "Second reply!", likeCount = 10)

            val threadItems =
                persistentListOf<ThreadItem>(
                    ThreadItem.Focus(focusPost),
                    ThreadItem.Reply(post = reply1, depth = 1),
                    ThreadItem.Reply(post = reply2, depth = 2),
                )
            repo.nextResult = Result.success(threadItems)

            viewModel.handleEvent(
                VideoCommentsEvent.OpenComments(
                    postUri = "at://focus",
                    initialDetent = VideoCommentsDetent.PartiallyExpanded,
                ),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("at://focus", state.postUri)
            assertEquals(VideoCommentsDetent.PartiallyExpanded, state.detent)
            assertTrue(state.isVisible)
            assertFalse(state.isRefreshing)
            assertEquals(2, state.comments.size)

            val firstComment = state.comments[0]
            assertEquals("at://reply1", firstComment.id)
            assertEquals("First reply!", firstComment.text)
            assertEquals(5L, firstComment.likeCount)
            assertEquals(1, firstComment.depth)

            val secondComment = state.comments[1]
            assertEquals("at://reply2", secondComment.id)
            assertEquals("Second reply!", secondComment.text)
            assertEquals(10L, secondComment.likeCount)
            assertEquals(2, secondComment.depth)

            assertTrue(state.status is VideoCommentsStatus.Loaded)
            assertEquals(listOf("at://focus"), repo.requestedUris)
            assertEquals(listOf(listOf(reply1, reply2)), cache.seededPosts)
        }

    @Test
    fun `OpenComments with zero replies transitions to Empty state`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            val focusPost = samplePost("at://focus", text = "Video with no replies")
            repo.nextResult = Result.success(persistentListOf(ThreadItem.Focus(focusPost)))

            viewModel.handleEvent(VideoCommentsEvent.OpenComments("at://focus"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.comments.isEmpty())
            assertTrue(state.isEmpty)
            assertEquals(VideoCommentsStatus.Empty, state.status)
        }

    @Test
    fun `OpenComments failure transitions to Error state when empty`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            repo.nextResult = Result.failure(IOException("Server error"))

            viewModel.handleEvent(VideoCommentsEvent.OpenComments("at://focus"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isError)
            val error = state.status as VideoCommentsStatus.Error
            assertEquals("Server error", error.message)
            assertTrue(state.comments.isEmpty())
        }

    @Test
    fun `SWR background revalidation preserves existing comments and reconciles updates`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            val reply1 = samplePost("at://reply1", text = "Initial comment", likeCount = 1)
            repo.nextResult =
                Result.success(
                    persistentListOf(
                        ThreadItem.Focus(samplePost("at://focus")),
                        ThreadItem.Reply(post = reply1, depth = 1),
                    ),
                )

            viewModel.handleEvent(VideoCommentsEvent.OpenComments("at://focus"))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.comments.size)

            // Setup fresh background data with a new reply added
            val reply2 = samplePost("at://reply2", text = "Fresh comment", likeCount = 0)
            repo.nextResult =
                Result.success(
                    persistentListOf(
                        ThreadItem.Focus(samplePost("at://focus")),
                        ThreadItem.Reply(post = reply1, depth = 1),
                        ThreadItem.Reply(post = reply2, depth = 1),
                    ),
                )

            // Re-open/refresh the same post
            viewModel.handleEvent(
                VideoCommentsEvent.OpenComments(
                    postUri = "at://focus",
                    initialDetent = VideoCommentsDetent.Expanded,
                ),
            )

            // While revalidating in background, comments remain visible
            assertTrue(viewModel.uiState.value.isRefreshing)
            assertEquals(1, viewModel.uiState.value.comments.size)

            advanceUntilIdle()

            val refreshedState = viewModel.uiState.value
            assertFalse(refreshedState.isRefreshing)
            assertEquals(2, refreshedState.comments.size)
            assertEquals(VideoCommentsDetent.Expanded, refreshedState.detent)
            assertTrue(refreshedState.status is VideoCommentsStatus.Loaded)
        }

    @Test
    fun `SWR background revalidation failure preserves comments and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            val reply = samplePost("at://reply1", text = "Existing comment")
            repo.nextResult =
                Result.success(
                    persistentListOf(ThreadItem.Reply(post = reply, depth = 1)),
                )

            viewModel.handleEvent(VideoCommentsEvent.OpenComments("at://focus"))
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.comments.size)

            // Background fetch fails
            repo.nextResult = Result.failure(IOException("Network dropped"))

            viewModel.effects.test {
                viewModel.handleEvent(VideoCommentsEvent.Refresh)
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is VideoCommentsEffect.ShowError)
                assertEquals("Network dropped", (effect as VideoCommentsEffect.ShowError).message)

                // Existing comments are still preserved
                val state = viewModel.uiState.value
                assertEquals(1, state.comments.size)
                assertFalse(state.isRefreshing)
                assertFalse(state.isError)
            }
        }

    @Test
    fun `DismissComments and SetDetent update detent state`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            viewModel.handleEvent(
                VideoCommentsEvent.OpenComments(
                    postUri = "at://focus",
                    initialDetent = VideoCommentsDetent.PartiallyExpanded,
                ),
            )
            assertEquals(VideoCommentsDetent.PartiallyExpanded, viewModel.uiState.value.detent)

            viewModel.handleEvent(VideoCommentsEvent.SetDetent(VideoCommentsDetent.Expanded))
            assertEquals(VideoCommentsDetent.Expanded, viewModel.uiState.value.detent)

            viewModel.handleEvent(VideoCommentsEvent.DismissComments)
            assertEquals(VideoCommentsDetent.Hidden, viewModel.uiState.value.detent)
            assertFalse(viewModel.uiState.value.isVisible)
        }

    @Test
    fun `LikeComment invokes cache toggleLike and surfaces error on failure`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            val reply = samplePost("at://reply1", cid = "cid1", text = "Like me")
            repo.nextResult =
                Result.success(
                    persistentListOf(ThreadItem.Reply(post = reply, depth = 1)),
                )
            viewModel.handleEvent(VideoCommentsEvent.OpenComments("at://focus"))
            advanceUntilIdle()

            viewModel.handleEvent(VideoCommentsEvent.LikeComment("at://reply1"))
            advanceUntilIdle()

            assertEquals(listOf("at://reply1" to "cid1"), cache.toggleLikeCalls)

            // Failure test
            cache.nextToggleLikeResult = Result.failure(IOException("Like failed"))
            viewModel.effects.test {
                viewModel.handleEvent(VideoCommentsEvent.LikeComment("at://reply1"))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is VideoCommentsEffect.ShowError)
                assertEquals("Like failed", (effect as VideoCommentsEffect.ShowError).message)
            }
        }

    @Test
    fun `cache interactions state updates propagate to loaded comments`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            val reply = samplePost("at://reply1", cid = "cid1", text = "Check like")
            repo.nextResult =
                Result.success(
                    persistentListOf(ThreadItem.Reply(post = reply, depth = 1)),
                )
            viewModel.handleEvent(VideoCommentsEvent.OpenComments("at://focus"))
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.comments[0]
                    .isLiked,
            )
            assertEquals(
                0L,
                viewModel.uiState.value.comments[0]
                    .likeCount,
            )

            // Emit updated like state from cache
            cache.emit(
                persistentMapOf(
                    "at://reply1" to
                        PostInteractionState(
                            viewerLikeUri = "at://like/123",
                            likeCount = 1,
                        ),
                ),
            )
            advanceUntilIdle()

            val updatedComment = viewModel.uiState.value.comments[0]
            assertTrue(updatedComment.isLiked)
            assertEquals(1L, updatedComment.likeCount)
        }

    @Test
    fun `RetryLoad re-triggers fetch for current postUri`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePostThreadRepository()
            val cache = FakePostInteractionsCache()
            val viewModel = VideoCommentsViewModel(repo, cache)

            repo.nextResult = Result.failure(IOException("Initial failure"))
            viewModel.handleEvent(VideoCommentsEvent.OpenComments("at://focus"))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isError)

            val reply = samplePost("at://reply1", text = "Recovered comment")
            repo.nextResult =
                Result.success(
                    persistentListOf(ThreadItem.Reply(post = reply, depth = 1)),
                )

            viewModel.handleEvent(VideoCommentsEvent.RetryLoad)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isError)
            assertEquals(1, viewModel.uiState.value.comments.size)
            assertEquals(listOf("at://focus", "at://focus"), repo.requestedUris)
        }

    private fun samplePost(
        id: String,
        cid: String = "cid_$id",
        text: String = "Post text",
        likeCount: Int = 0,
        isLiked: Boolean = false,
    ): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author =
                AuthorUi(
                    did = "did:plc:author",
                    handle = "author.bsky.social",
                    displayName = "Author",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-10-20T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(likeCount = likeCount),
            viewer =
                ViewerStateUi(
                    isLikedByViewer = isLiked,
                    likeUri = if (isLiked) "at://like" else null,
                ),
            repostedBy = null,
        )

    private class FakePostThreadRepository : PostThreadRepository {
        var nextResult: Result<ImmutableList<ThreadItem>> = Result.success(persistentListOf())
        val requestedUris = mutableListOf<String>()

        override suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>> {
            requestedUris += uri
            return nextResult
        }
    }

    private class FakePostInteractionsCache : PostInteractionsCache {
        private val _state = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
        override val state: StateFlow<PersistentMap<String, PostInteractionState>> = _state.asStateFlow()

        val seededPosts = mutableListOf<List<PostUi>>()
        val toggleLikeCalls = mutableListOf<Pair<String, String>>()
        var nextToggleLikeResult: Result<Unit> = Result.success(Unit)

        override fun seed(posts: List<PostUi>) {
            seededPosts += posts
        }

        override suspend fun toggleLike(
            postUri: String,
            postCid: String,
        ): Result<Unit> {
            toggleLikeCalls += (postUri to postCid)
            return nextToggleLikeResult
        }

        override suspend fun toggleRepost(
            postUri: String,
            postCid: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun toggleBookmark(
            postUri: String,
            postCid: String,
        ): Result<Unit> = Result.success(Unit)

        override fun markDeleted(postUri: String) {}

        override fun clear() {}

        fun emit(map: PersistentMap<String, PostInteractionState>) {
            _state.value = map
        }
    }
}
