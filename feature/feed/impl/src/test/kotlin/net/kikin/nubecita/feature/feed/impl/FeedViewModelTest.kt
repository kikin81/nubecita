package net.kikin.nubecita.feature.feed.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
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
import net.kikin.nubecita.feature.feed.impl.data.LikeRepostRepository
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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is FeedLoadStatus.InitialError)
            assertEquals(FeedError.Unauthenticated, (status as FeedLoadStatus.InitialError).error)
        }

    @Test
    fun `OnLikeClicked optimistically flips, persists likeUri on success`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = samplePost("at://repo/app.bsky.feed.post/p1", stats = PostStatsUi(likeCount = 5))
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(persistentListOf(FeedItemUi.Single(initial)), null))))
            val createdLikeUri = "at://repo/app.bsky.feed.like/3lk1"
            val likeRepo =
                FakeLikeRepostRepository(
                    likeResult = { Result.success(AtUri(createdLikeUri)) },
                )
            val vm = FeedViewModel(repo, likeRepo)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.OnLikeClicked(initial))
            // After dispatch but before the suspending repo call resolves, the
            // optimistic flip must be visible. Using runTest(dispatcher) the
            // launch enqueues a child coroutine — read state without
            // advanceUntilIdle to observe the synchronous setState first.
            val optimistic =
                vm.uiState.value.feedItems
                    .first()
                    .leafPost()
            assertTrue(optimistic.viewer.isLikedByViewer)
            assertEquals(6, optimistic.stats.likeCount)
            assertEquals(null, optimistic.viewer.likeUri)

            advanceUntilIdle()
            val final =
                vm.uiState.value.feedItems
                    .first()
                    .leafPost()
            assertTrue(final.viewer.isLikedByViewer)
            assertEquals(6, final.stats.likeCount)
            assertEquals(createdLikeUri, final.viewer.likeUri)
            assertEquals(1, likeRepo.likeCalls.size)
        }

    @Test
    fun `OnLikeClicked rolls back on failure and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = samplePost("at://repo/app.bsky.feed.post/p1", stats = PostStatsUi(likeCount = 5))
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(persistentListOf(FeedItemUi.Single(initial)), null))))
            val likeRepo = FakeLikeRepostRepository(likeResult = { Result.failure(IOException("nope")) })
            val vm = FeedViewModel(repo, likeRepo)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnLikeClicked(initial))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError, "expected ShowError, got $effect")
                assertEquals(FeedError.Network, (effect as FeedEffect.ShowError).error)
            }

            val rolledBack =
                vm.uiState.value.feedItems
                    .first()
                    .leafPost()
            assertEquals(false, rolledBack.viewer.isLikedByViewer)
            assertEquals(5, rolledBack.stats.likeCount)
            assertEquals(null, rolledBack.viewer.likeUri)
        }

    @Test
    fun `OnLikeClicked on a previously-liked post unlikes (count decrements, likeUri cleared)`() =
        runTest(mainDispatcher.dispatcher) {
            val likeUri = "at://repo/app.bsky.feed.like/existing"
            val initial =
                samplePost(
                    id = "at://repo/app.bsky.feed.post/p1",
                    stats = PostStatsUi(likeCount = 7),
                    viewer = ViewerStateUi(isLikedByViewer = true, likeUri = likeUri),
                )
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(persistentListOf(FeedItemUi.Single(initial)), null))))
            val likeRepo = FakeLikeRepostRepository(unlikeResult = { Result.success(Unit) })
            val vm = FeedViewModel(repo, likeRepo)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.OnLikeClicked(initial))
            advanceUntilIdle()

            val final =
                vm.uiState.value.feedItems
                    .first()
                    .leafPost()
            assertEquals(false, final.viewer.isLikedByViewer)
            assertEquals(6, final.stats.likeCount)
            assertEquals(null, final.viewer.likeUri)
            assertEquals(listOf(AtUri(likeUri)), likeRepo.unlikeCalls)
        }

    @Test
    fun `OnRepostClicked optimistically flips, persists repostUri on success`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = samplePost("at://repo/app.bsky.feed.post/p1", stats = PostStatsUi(repostCount = 2))
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(persistentListOf(FeedItemUi.Single(initial)), null))))
            val createdRepostUri = "at://repo/app.bsky.feed.repost/3lr1"
            val likeRepo = FakeLikeRepostRepository(repostResult = { Result.success(AtUri(createdRepostUri)) })
            val vm = FeedViewModel(repo, likeRepo)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.OnRepostClicked(initial))
            advanceUntilIdle()

            val final =
                vm.uiState.value.feedItems
                    .first()
                    .leafPost()
            assertTrue(final.viewer.isRepostedByViewer)
            assertEquals(3, final.stats.repostCount)
            assertEquals(createdRepostUri, final.viewer.repostUri)
        }

    @Test
    fun `OnRepostClicked rolls back on failure and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = samplePost("at://repo/app.bsky.feed.post/p1", stats = PostStatsUi(repostCount = 2))
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(persistentListOf(FeedItemUi.Single(initial)), null))))
            val likeRepo = FakeLikeRepostRepository(repostResult = { Result.failure(IOException("server gone")) })
            val vm = FeedViewModel(repo, likeRepo)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnRepostClicked(initial))
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError, "expected ShowError, got $effect")
            }

            val rolledBack =
                vm.uiState.value.feedItems
                    .first()
                    .leafPost()
            assertEquals(false, rolledBack.viewer.isRepostedByViewer)
            assertEquals(2, rolledBack.stats.repostCount)
            assertEquals(null, rolledBack.viewer.repostUri)
        }

    @Test
    fun `OnLikeClicked targeting the cluster root updates only that post`() =
        runTest(mainDispatcher.dispatcher) {
            val rootPost = samplePost(id = "at://repo/app.bsky.feed.post/root", stats = PostStatsUi(likeCount = 1))
            val parentPost = samplePost(id = "at://repo/app.bsky.feed.post/parent", stats = PostStatsUi(likeCount = 2))
            val leafPost = samplePost(id = "at://repo/app.bsky.feed.post/leaf", stats = PostStatsUi(likeCount = 3))
            val cluster =
                FeedItemUi.ReplyCluster(
                    root = rootPost,
                    parent = parentPost,
                    leaf = leafPost,
                    hasEllipsis = false,
                )
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(persistentListOf<FeedItemUi>(cluster), null))))
            val likeRepo = FakeLikeRepostRepository(likeResult = { Result.success(AtUri("at://repo/app.bsky.feed.like/r")) })
            val vm = FeedViewModel(repo, likeRepo)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.OnLikeClicked(rootPost))
            advanceUntilIdle()

            val updated =
                vm.uiState.value.feedItems
                    .first() as FeedItemUi.ReplyCluster
            assertTrue(updated.root.viewer.isLikedByViewer)
            assertEquals(2, updated.root.stats.likeCount)
            // Parent and leaf untouched; reference identity preserved.
            assertSame(parentPost, updated.parent)
            assertSame(leafPost, updated.leaf)
        }

    @Test
    fun `OnReplyClicked is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())
            val before = vm.uiState.value
            val post = samplePost("p1")

            vm.handleEvent(FeedEvent.OnReplyClicked(post))
            advanceUntilIdle()

            assertSame(before, vm.uiState.value)
        }

    @Test
    fun `OnShareClicked emits SharePost with bsky_app permalink as the share text`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())
            val before = vm.uiState.value
            val post =
                samplePost("p1").copy(
                    id = "at://did:plc:fake/app.bsky.feed.post/3krkey1",
                )

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnShareClicked(post))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.SharePost, "expected SharePost, got $effect")
                val intent = (effect as FeedEffect.SharePost).intent
                assertEquals(
                    "https://bsky.app/profile/fake.bsky.social/post/3krkey1",
                    intent.permalink,
                )
                assertEquals(intent.permalink, intent.text)
            }
            // No state mutation — share is a pure side effect.
            assertSame(before, vm.uiState.value)
        }

    @Test
    fun `OnShareLongPressed emits CopyPermalink (no surrounding share text)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())
            val post =
                samplePost("p1").copy(
                    id = "at://did:plc:fake/app.bsky.feed.post/3krkey9",
                )

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnShareLongPressed(post))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.CopyPermalink, "expected CopyPermalink, got $effect")
                assertEquals(
                    "https://bsky.app/profile/fake.bsky.social/post/3krkey9",
                    (effect as FeedEffect.CopyPermalink).permalink,
                )
            }
        }

    @Test
    fun `OnPostTapped emits NavigateToPost with the tapped post`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())
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
            val vm = FeedViewModel(repo, FakeLikeRepostRepository())

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnAuthorTapped("did:plc:alice"))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToAuthor)
                assertEquals("did:plc:alice", (effect as FeedEffect.NavigateToAuthor).authorDid)
            }
        }
}

private fun feedItems(vararg ids: String): ImmutableList<FeedItemUi> = ids.map { FeedItemUi.Single(samplePost(it)) }.toImmutableList()

private fun samplePost(
    id: String,
    cid: String = "bafyreifakefakefakefakefakefakefakefakefakefake",
    stats: PostStatsUi = PostStatsUi(),
    viewer: ViewerStateUi = ViewerStateUi(),
): PostUi =
    PostUi(
        id = id,
        cid = cid,
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
        stats = stats,
        viewer = viewer,
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

/**
 * Closure-based fake — each operation has its own per-call result lambda
 * so individual tests can return success/failure without subclassing.
 * Records the args (StrongRef / AtUri) for assertions on what got passed.
 */
private class FakeLikeRepostRepository(
    private val likeResult: (suspend () -> Result<AtUri>)? = null,
    private val unlikeResult: (suspend () -> Result<Unit>)? = null,
    private val repostResult: (suspend () -> Result<AtUri>)? = null,
    private val unrepostResult: (suspend () -> Result<Unit>)? = null,
) : LikeRepostRepository {
    val likeCalls = mutableListOf<StrongRef>()
    val unlikeCalls = mutableListOf<AtUri>()
    val repostCalls = mutableListOf<StrongRef>()
    val unrepostCalls = mutableListOf<AtUri>()

    override suspend fun like(post: StrongRef): Result<AtUri> {
        likeCalls += post
        return likeResult?.invoke() ?: error("unexpected like($post)")
    }

    override suspend fun unlike(likeUri: AtUri): Result<Unit> {
        unlikeCalls += likeUri
        return unlikeResult?.invoke() ?: error("unexpected unlike($likeUri)")
    }

    override suspend fun repost(post: StrongRef): Result<AtUri> {
        repostCalls += post
        return repostResult?.invoke() ?: error("unexpected repost($post)")
    }

    override suspend fun unrepost(repostUri: AtUri): Result<Unit> {
        unrepostCalls += repostUri
        return unrepostResult?.invoke() ?: error("unexpected unrepost($repostUri)")
    }
}

/**
 * Convenience accessor used by the like/repost tests — the timeline
 * fixtures register a single Single feed entry, so `first().leafPost()`
 * returns the post under test regardless of the FeedItemUi shape.
 */
private fun FeedItemUi.leafPost(): PostUi =
    when (this) {
        is FeedItemUi.Single -> post
        is FeedItemUi.ReplyCluster -> leaf
    }
