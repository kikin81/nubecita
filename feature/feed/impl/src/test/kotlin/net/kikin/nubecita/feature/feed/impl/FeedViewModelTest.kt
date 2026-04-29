package net.kikin.nubecita.feature.feed.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial Load success populates posts, advances cursor, returns to Idle`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1", "p2"), nextCursor = "c1"))),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(2, state.feedItems.size)
            assertEquals("c1", state.nextCursor)
            assertEquals(false, state.endReached)
            assertEquals(FeedLoadStatus.Idle, state.loadStatus)
            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `initial Load with empty page sets endReached`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = persistentListOf(), nextCursor = null))),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.feedItems.isEmpty())
            assertEquals(true, state.endReached)
            assertEquals(FeedLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `initial Load failure populates InitialError(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository(pages = listOf(Result.failure(IOException("network down"))))
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is FeedLoadStatus.InitialError, "expected InitialError, got $status")
            assertEquals(FeedError.Network, (status as FeedLoadStatus.InitialError).error)
            assertTrue(
                vm.uiState.value.feedItems
                    .isEmpty(),
            )
        }

    @Test
    fun `Retry after initial load failure re-runs the initial load on success`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.failure(IOException("transient")),
                            Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = "c1")),
                        ),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is FeedLoadStatus.InitialError)

            vm.handleEvent(FeedEvent.Retry)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, state.feedItems.size)
            assertEquals(FeedLoadStatus.Idle, state.loadStatus)
            assertEquals(2, repo.invocations.size)
        }

    @Test
    fun `Refresh success replaces posts and advances cursor`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(TimelinePage(feedItems = feedItems("p1", "p2"), nextCursor = "c1")),
                            Result.success(TimelinePage(feedItems = feedItems("p3"), nextCursor = "c2")),
                        ),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.Refresh)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf("p3"), state.feedItems.map { it.key })
            assertEquals("c2", state.nextCursor)
            assertEquals(FeedLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Refresh failure preserves posts and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = "c1")),
                            Result.failure(IOException("refresh failed")),
                        ),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedEvent.Refresh)
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError)
                assertEquals(FeedError.Network, (effect as FeedEffect.ShowError).error)
            }

            val state = vm.uiState.value
            assertEquals(listOf("p1"), state.feedItems.map { it.key })
            assertEquals("c1", state.nextCursor)
            assertEquals(FeedLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `LoadMore success appends posts, advances cursor, and de-dupes by id`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(TimelinePage(feedItems = feedItems("p1", "p2"), nextCursor = "c1")),
                            // Page 2 deliberately repeats p2 (server-side cursor desync) — must be deduped.
                            Result.success(TimelinePage(feedItems = feedItems("p2", "p3", "p4"), nextCursor = "c2")),
                        ),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.LoadMore)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf("p1", "p2", "p3", "p4"), state.feedItems.map { it.key })
            assertEquals("c2", state.nextCursor)
            assertEquals(FeedLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `LoadMore failure preserves cursor and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = "c1")),
                            Result.failure(IOException("page fetch failed")),
                        ),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedEvent.LoadMore)
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError)
            }

            val state = vm.uiState.value
            assertEquals(listOf("p1"), state.feedItems.map { it.key })
            // Cursor preserved so retry can replay against the same page boundary.
            assertEquals("c1", state.nextCursor)
            assertEquals(FeedLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `LoadMore at end-of-feed is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            assertEquals(true, vm.uiState.value.endReached)

            // Second invocation must not happen even if LoadMore is dispatched.
            vm.handleEvent(FeedEvent.LoadMore)
            advanceUntilIdle()

            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `Load while InitialLoading is idempotent (no second repo call)`() =
        runTest(mainDispatcher.dispatcher) {
            val first = CompletableDeferred<Result<TimelinePage>>()
            val repo =
                FakeFeedRepository(
                    pageProducer = { _, _ ->
                        first.await()
                    },
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            // Don't yet complete the deferred; while it's pending the second
            // Load must be a no-op.
            vm.handleEvent(FeedEvent.Load)
            vm.handleEvent(FeedEvent.Load)

            first.complete(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null)))
            advanceUntilIdle()

            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `Refresh while InitialLoading is a no-op (mutually exclusive load modes)`() =
        runTest(mainDispatcher.dispatcher) {
            val first = CompletableDeferred<Result<TimelinePage>>()
            val repo = FakeFeedRepository(pageProducer = { _, _ -> first.await() })
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            // VM is now in InitialLoading; Refresh must be dropped.
            vm.handleEvent(FeedEvent.Refresh)

            first.complete(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null)))
            advanceUntilIdle()

            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `Refresh while Refreshing is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = "c1"))
            val refreshDeferred = CompletableDeferred<Result<TimelinePage>>()
            var call = 0
            val repo =
                FakeFeedRepository(
                    pageProducer = { _, _ ->
                        when (call++) {
                            0 -> initial // initial Load
                            else -> refreshDeferred.await() // first Refresh
                        }
                    },
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.Refresh) // enters Refreshing
            // Second Refresh while still Refreshing must be dropped — no third call.
            vm.handleEvent(FeedEvent.Refresh)

            refreshDeferred.complete(Result.success(TimelinePage(feedItems = feedItems("p2"), nextCursor = "c2")))
            advanceUntilIdle()

            assertEquals(2, repo.invocations.size)
        }

    @Test
    fun `LoadMore while Refreshing is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = "c1"))
            val refreshDeferred = CompletableDeferred<Result<TimelinePage>>()
            var call = 0
            val repo =
                FakeFeedRepository(
                    pageProducer = { _, _ ->
                        when (call++) {
                            0 -> initial
                            else -> refreshDeferred.await()
                        }
                    },
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.Refresh) // enters Refreshing
            // LoadMore while a refresh is in flight would otherwise race the
            // refresh's setState. The guard drops the event.
            vm.handleEvent(FeedEvent.LoadMore)

            refreshDeferred.complete(Result.success(TimelinePage(feedItems = feedItems("p2"), nextCursor = "c2")))
            advanceUntilIdle()

            assertEquals(2, repo.invocations.size)
        }

    @Test
    fun `LoadMore while another LoadMore is in flight is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = "c1"))
            val appendDeferred = CompletableDeferred<Result<TimelinePage>>()
            var call = 0
            val repo =
                FakeFeedRepository(
                    pageProducer = { _, _ ->
                        when (call++) {
                            0 -> initial
                            else -> appendDeferred.await()
                        }
                    },
                )
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.LoadMore) // enters Appending
            vm.handleEvent(FeedEvent.LoadMore) // dropped

            appendDeferred.complete(Result.success(TimelinePage(feedItems = feedItems("p2"), nextCursor = "c2")))
            advanceUntilIdle()

            assertEquals(2, repo.invocations.size)
        }

    @Test
    fun `NoSessionException maps to InitialError(Unauthenticated)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository(pages = listOf(Result.failure(NoSessionException())))
            val vm = FeedViewModel(repo)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is FeedLoadStatus.InitialError)
            assertEquals(FeedError.Unauthenticated, (status as FeedLoadStatus.InitialError).error)
        }

    @Test
    fun `OnLikeClicked is a no-op (no state mutation, no effect)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo)
            val before = vm.uiState.value
            val post = samplePost("p1")

            vm.handleEvent(FeedEvent.OnLikeClicked(post))
            advanceUntilIdle()

            assertSame(before, vm.uiState.value)
            assertEquals(0, repo.invocations.size)
        }

    @Test
    fun `OnRepostClicked, OnReplyClicked, OnShareClicked are no-ops`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo)
            val before = vm.uiState.value
            val post = samplePost("p1")

            vm.handleEvent(FeedEvent.OnRepostClicked(post))
            vm.handleEvent(FeedEvent.OnReplyClicked(post))
            vm.handleEvent(FeedEvent.OnShareClicked(post))
            advanceUntilIdle()

            assertSame(before, vm.uiState.value)
            assertEquals(0, repo.invocations.size)
        }

    @Test
    fun `OnPostTapped emits NavigateToPost with the tapped post`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo)
            val post = samplePost("p1")

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnPostTapped(post))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToPost)
                assertSame(post, (effect as FeedEffect.NavigateToPost).post)
            }
        }

    @Test
    fun `OnAuthorTapped emits NavigateToAuthor with the author DID`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo)

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnAuthorTapped("did:plc:alice"))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToAuthor)
                assertEquals("did:plc:alice", (effect as FeedEffect.NavigateToAuthor).authorDid)
            }
        }
}

private fun feedItems(vararg ids: String): ImmutableList<FeedItemUi> = ids.map { FeedItemUi.Single(samplePost(it)) }.toImmutableList()

private fun samplePost(id: String): PostUi =
    PostUi(
        id = id,
        author =
            AuthorUi(
                did = "did:plc:fake",
                handle = "fake.bsky.social",
                displayName = "Fake",
                avatarUrl = null,
            ),
        createdAt = Instant.parse("2026-04-25T12:00:00Z"),
        text = "fake text $id",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

private class FakeFeedRepository(
    pages: List<Result<TimelinePage>> = emptyList(),
    private val pageProducer: (suspend (cursor: String?, limit: Int) -> Result<TimelinePage>)? = null,
) : FeedRepository {
    private val pageQueue = ArrayDeque(pages)
    val invocations = mutableListOf<Pair<String?, Int>>()

    override suspend fun getTimeline(
        cursor: String?,
        limit: Int,
    ): Result<TimelinePage> {
        invocations += cursor to limit
        return pageProducer?.invoke(cursor, limit)
            ?: pageQueue.removeFirstOrNull()
            ?: error("FakeFeedRepository got an unexpected getTimeline call ($cursor, $limit)")
    }
}
