package net.kikin.nubecita.feature.postdetail.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
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

    // ---------- helpers ----------

    private fun newVm(
        repo: PostThreadRepository,
        focusUri: String = "at://focus",
    ): PostDetailViewModel =
        PostDetailViewModel(
            route = PostDetailRoute(postUri = focusUri),
            postThreadRepository = repo,
        )

    private fun samplePost(
        id: String,
        text: String = "sample text",
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
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
