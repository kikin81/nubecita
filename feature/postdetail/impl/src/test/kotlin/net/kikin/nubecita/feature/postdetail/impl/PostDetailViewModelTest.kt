package net.kikin.nubecita.feature.postdetail.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.postdetail.impl.data.PostThreadRepository
import net.kikin.nubecita.feature.postdetail.impl.data.ThreadItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class PostDetailViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial Load success populates items and returns to Idle`() =
        runTest(mainDispatcher.dispatcher) {
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(items, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
            assertEquals(listOf("at://focus"), repo.invocations)
        }

    @Test
    fun `initial Load success with empty thread surfaces InitialError(NotFound)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(results = listOf(Result.success(persistentListOf())))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.NotFound, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `IOException maps to InitialError(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(results = listOf(Result.failure(IOException("network down"))))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.Network, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `NoSessionException maps to InitialError(Unauthenticated)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(results = listOf(Result.failure(NoSessionException())))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.Unauthenticated, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `XrpcError with errorName=NotFound maps to InitialError(NotFound)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    results = listOf(Result.failure(XrpcError.Unknown(name = "NotFound", message = null, status = 400))),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.NotFound, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `XrpcError with status=404 maps to InitialError(NotFound)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    results = listOf(Result.failure(XrpcError.Unknown(name = "Other", message = null, status = 404))),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.NotFound, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `unknown XrpcError maps to InitialError(Unknown) carrying errorName`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    results = listOf(Result.failure(XrpcError.Unknown(name = "ServerError", message = null, status = 500))),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            val error = (status as PostDetailLoadStatus.InitialError).error
            assertTrue(error is PostDetailError.Unknown)
            assertEquals("ServerError", (error as PostDetailError.Unknown).cause)
        }

    @Test
    fun `Retry after InitialError re-runs the initial load`() =
        runTest(mainDispatcher.dispatcher) {
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.failure(IOException("transient")),
                            Result.success(items),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is PostDetailLoadStatus.InitialError)

            vm.handleEvent(PostDetailEvent.Retry)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(items, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
            assertEquals(2, repo.invocations.size)
        }

    @Test
    fun `Refresh success replaces items`() =
        runTest(mainDispatcher.dispatcher) {
            val firstItems = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus", text = "v1")))
            val refreshedItems =
                persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus", text = "v2")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.success(firstItems),
                            Result.success(refreshedItems),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(PostDetailEvent.Refresh)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(refreshedItems, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Refresh failure preserves items and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val firstItems = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.success(firstItems),
                            Result.failure(IOException("refresh failed")),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.Refresh)
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.ShowError)
                assertEquals(PostDetailError.Network, (effect as PostDetailEffect.ShowError).error)
            }

            val state = vm.uiState.value
            assertEquals(firstItems, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Refresh with empty result preserves prior items`() =
        runTest(mainDispatcher.dispatcher) {
            val firstItems = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.success(firstItems),
                            Result.success(persistentListOf()),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(PostDetailEvent.Refresh)
            advanceUntilIdle()

            val state = vm.uiState.value
            // Empty refresh response keeps the prior items and stays Idle
            // — better than wiping the screen mid-read.
            assertEquals(firstItems, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Load while InitialLoading is idempotent (no second repo call)`() =
        runTest(mainDispatcher.dispatcher) {
            val deferred = CompletableDeferred<Result<ImmutableList<ThreadItem>>>()
            val repo = FakeRepo(producer = { deferred.await() })
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            // Don't yet complete the deferred; while it's pending the second
            // Load must be a no-op.
            vm.handleEvent(PostDetailEvent.Load)
            vm.handleEvent(PostDetailEvent.Load)

            deferred.complete(
                Result.success(persistentListOf(ThreadItem.Focus(samplePost("at://focus")))),
            )
            advanceUntilIdle()

            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `Refresh while InitialLoading is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val deferred = CompletableDeferred<Result<ImmutableList<ThreadItem>>>()
            val repo = FakeRepo(producer = { deferred.await() })
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            // VM is now in InitialLoading; Refresh must be dropped.
            vm.handleEvent(PostDetailEvent.Refresh)

            deferred.complete(
                Result.success(persistentListOf(ThreadItem.Focus(samplePost("at://focus")))),
            )
            advanceUntilIdle()

            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `OnPostTapped emits NavigateToPost with the URI`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnPostTapped("at://target"))

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToPost)
                assertEquals("at://target", (effect as PostDetailEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `OnAuthorTapped emits NavigateToAuthor with the DID`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnAuthorTapped("did:plc:alice"))

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToAuthor)
                assertEquals("did:plc:alice", (effect as PostDetailEffect.NavigateToAuthor).authorDid)
            }
        }

    @Test
    fun `OnQuotedPostTapped emits NavigateToPost with the quoted post's URI`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnQuotedPostTapped("at://quoted-target"))

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToPost)
                assertEquals("at://quoted-target", (effect as PostDetailEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `tapping the same focus URI emits NavigateToPost (no special-case)`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo(), focusUri = "at://focus")

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnPostTapped("at://focus"))
                val effect = awaitItem()
                // VM doesn't dedupe — re-entering the same focus is harmless
                // (the new NavEntry's state holder picks up at the same scroll
                // position) and keeping it dumb keeps the contract small.
                assertTrue(effect is PostDetailEffect.NavigateToPost)
                assertEquals("at://focus", (effect as PostDetailEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `OnReplyClicked after a successful Load emits NavigateToComposer with the focus URI`() =
        runTest(mainDispatcher.dispatcher) {
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, focusUri = "at://focus")

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnReplyClicked)
                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToComposer)
                assertEquals("at://focus", (effect as PostDetailEffect.NavigateToComposer).parentPostUri)
            }
        }

    @Test
    fun `OnReplyClicked while no Focus is loaded is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            // VM is in default Idle state with no items — the FAB should
            // still be visible (it lives in Scaffold's slot regardless of
            // load status), but tapping it before a Focus resolves is a
            // silent drop, not an arbitrary effect.
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnReplyClicked)
                expectNoEvents()
            }
        }

    @Test
    fun `OnFocusImageClicked emits NavigateToMediaViewer with the focus URI and image index`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo(), focusUri = "at://focus")

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnFocusImageClicked(imageIndex = 2))
                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToMediaViewer)
                val nav = effect as PostDetailEffect.NavigateToMediaViewer
                assertEquals("at://focus", nav.postUri)
                assertEquals(2, nav.imageIndex)
            }
        }

    // ---------- cache interaction tests ----------

    @Test
    fun `OnLikeClicked dispatches cache toggleLike with focused post id and cid`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val vm = newVm(FakeRepo(), cache = cache)
            val post = samplePost("at://x", cid = "bafyX")

            vm.handleEvent(PostDetailEvent.OnLikeClicked(post))
            advanceUntilIdle()

            assertEquals(1, cache.toggleLikeCalls.get())
            assertEquals("at://x" to "bafyX", cache.lastToggleLikeArgs.last())
        }

    @Test
    fun `OnLikeClicked failure surfaces PostDetailEffect_ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            cache.nextToggleLikeResult = Result.failure(IOException("like failed"))
            val vm = newVm(FakeRepo(), cache = cache)
            val post = samplePost("at://x")

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnLikeClicked(post))
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.ShowError)
                assertEquals(PostDetailError.Network, (effect as PostDetailEffect.ShowError).error)
            }
        }

    @Test
    fun `cache emission projects onto Focus and Reply thread items`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val focusPost = samplePost("at://focus")
            val replyPost = samplePost("at://reply")
            val items =
                persistentListOf<ThreadItem>(
                    ThreadItem.Focus(focusPost),
                    ThreadItem.Reply(replyPost, depth = 1),
                )
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, cache = cache)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            // Emit a cache state with updated interaction for both posts.
            val focusState = PostInteractionState(viewerLikeUri = "at://likeuri-focus", likeCount = 5L)
            val replyState = PostInteractionState(viewerLikeUri = "at://likeuri-reply", likeCount = 3L)
            cache.emit(
                persistentMapOf(
                    "at://focus" to focusState,
                    "at://reply" to replyState,
                ),
            )
            advanceUntilIdle()

            val state = vm.uiState.value
            val focusItem = state.items.filterIsInstance<ThreadItem.Focus>().first()
            val replyItem = state.items.filterIsInstance<ThreadItem.Reply>().first()

            assertTrue(focusItem.post.viewer.isLikedByViewer)
            assertEquals(5, focusItem.post.stats.likeCount)
            assertTrue(replyItem.post.viewer.isLikedByViewer)
            assertEquals(3, replyItem.post.stats.likeCount)
        }

    // ---------- helpers ----------

    private fun newVm(
        repo: PostThreadRepository,
        focusUri: String = "at://focus",
        cache: PostInteractionsCache = FakePostInteractionsCache(),
    ): PostDetailViewModel =
        PostDetailViewModel(
            route = PostDetailRoute(postUri = focusUri),
            postThreadRepository = repo,
            postInteractionsCache = cache,
        )

    private fun samplePost(
        id: String,
        text: String = "sample text",
        cid: String = "bafyreifakefakefakefakefakefakefakefakefakefake",
    ): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author =
                AuthorUi(
                    did = "did:plc:test",
                    handle = "test.bsky.social",
                    displayName = "Test",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-04-25T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )

    private class FakeRepo(
        results: List<Result<ImmutableList<ThreadItem>>> = emptyList(),
        private val producer: (suspend (uri: String) -> Result<ImmutableList<ThreadItem>>)? = null,
    ) : PostThreadRepository {
        private val queue = ArrayDeque(results)
        val invocations = mutableListOf<String>()

        override suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>> {
            invocations += uri
            return producer?.invoke(uri)
                ?: queue.removeFirstOrNull()
                ?: error("FakeRepo got an unexpected getPostThread($uri) call")
        }
    }
}
