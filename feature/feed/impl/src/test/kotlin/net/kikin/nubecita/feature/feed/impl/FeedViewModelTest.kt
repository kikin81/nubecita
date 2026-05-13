package net.kikin.nubecita.feature.feed.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import net.kikin.nubecita.feature.feed.impl.data.toFeedItemsUi
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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
    fun `Load is a no-op once feedItems is populated (re-entry into composition)`() =
        // Pin: FeedScreen's `LaunchedEffect(Unit) { Load }` re-fires each
        // time the screen re-enters composition (composer route pops,
        // tab switch back, etc.). Without this guard the second Load
        // wholesale-replaces `feedItems` from a fresh fetch — losing
        // scroll position, any optimistic mutations (like / repost /
        // replyCount), and the cursor cluster-context dedupe state.
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1", "p2"), nextCursor = "c1"))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            assertEquals(1, repo.invocations.size)
            val afterFirstLoad = vm.uiState.value

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            // No additional repo call, and the prior state slice is preserved
            // verbatim (referential equality on feedItems means LazyColumn
            // skips recomposition for unchanged items).
            assertEquals(1, repo.invocations.size)
            assertSame(afterFirstLoad, vm.uiState.value)
        }

    @Test
    fun `initial Load with empty page sets endReached`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = persistentListOf(), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is FeedLoadStatus.InitialError)
            assertEquals(FeedError.Unauthenticated, (status as FeedLoadStatus.InitialError).error)
        }

    // ---------- interaction dispatch + routing tests ----------

    @Test
    fun `OnLikeClicked dispatches cache toggleLike with post id and cid`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("at://post-a"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, cache)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            val post = samplePost(id = "at://post-a", cid = "bafyA")

            vm.handleEvent(FeedEvent.OnLikeClicked(post))
            advanceUntilIdle()

            assertEquals(1, cache.toggleLikeCalls.get())
            assertEquals("at://post-a" to "bafyA", cache.lastToggleLikeArgs.last())
        }

    @Test
    fun `OnLikeClicked routes cache failure to FeedEffect_ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val cache =
                FakePostInteractionsCache().apply {
                    nextToggleLikeResult = Result.failure(IOException("net down"))
                }
            val vm = FeedViewModel(FakeFeedRepository(), cache)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnLikeClicked(samplePost(id = "at://post-x", cid = "bafyX")))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError, "MUST emit ShowError on cache failure")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cache state emission projects onto feedItems`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val postId = "at://did:plc:alice/app.bsky.feed.post/p1"
            val post = samplePost(id = postId)
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(persistentListOf(FeedItemUi.Single(post)), null))),
                )
            val vm = FeedViewModel(repo, cache)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            cache.emit(
                persistentMapOf(
                    postId to
                        PostInteractionState(
                            viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/test",
                            likeCount = 99L,
                        ),
                ),
            )
            advanceUntilIdle()

            val merged =
                vm.uiState.value.feedItems
                    .filterIsInstance<FeedItemUi.Single>()
                    .first { it.post.id == postId }
            assertTrue(merged.post.viewer.isLikedByViewer)
            assertEquals(99, merged.post.stats.likeCount)
        }

    @Test
    fun `OnRepostClicked dispatches cache toggleRepost with post id and cid`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("at://post-b"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, cache)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            val post = samplePost(id = "at://post-b", cid = "bafyB")

            vm.handleEvent(FeedEvent.OnRepostClicked(post))
            advanceUntilIdle()

            assertEquals(1, cache.toggleRepostCalls.get())
            assertEquals("at://post-b" to "bafyB", cache.lastToggleRepostArgs.last())
        }

    @Test
    fun `OnRepostClicked routes cache failure to FeedEffect_ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val cache =
                FakePostInteractionsCache().apply {
                    nextToggleRepostResult = Result.failure(IOException("net down"))
                }
            val vm = FeedViewModel(FakeFeedRepository(), cache)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnRepostClicked(samplePost(id = "at://post-y", cid = "bafyY")))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError, "MUST emit ShowError on cache failure")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cache repost state emission projects onto feedItems`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val postId = "at://did:plc:alice/app.bsky.feed.post/p2"
            val post = samplePost(id = postId)
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(persistentListOf(FeedItemUi.Single(post)), null))),
                )
            val vm = FeedViewModel(repo, cache)
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            cache.emit(
                persistentMapOf(
                    postId to
                        PostInteractionState(
                            viewerRepostUri = "at://did:plc:viewer/app.bsky.feed.repost/test",
                            repostCount = 42L,
                        ),
                ),
            )
            advanceUntilIdle()

            val merged =
                vm.uiState.value.feedItems
                    .filterIsInstance<FeedItemUi.Single>()
                    .first { it.post.id == postId }
            assertTrue(merged.post.viewer.isRepostedByViewer)
            assertEquals(42, merged.post.stats.repostCount)
        }

    // ---------- reply-count tests ----------

    @Test
    fun `OnReplySubmittedToParent increments parent replyCount by 1`() =
        runTest(mainDispatcher.dispatcher) {
            // Pin: when the composer reports a successful reply submit
            // via LocalComposerSubmitEvents, the feed runs an
            // optimistic replyCount + 1 on the parent so the user
            // doesn't see a stale "0 comments" on the post they just
            // replied to. No network call needed — the submit success
            // is the trigger.
            val parentUri = "at://did:plc:author/app.bsky.feed.post/p1"
            val parent = samplePost(parentUri, stats = PostStatsUi(replyCount = 3))
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(persistentListOf(FeedItemUi.Single(parent)), null),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.OnReplySubmittedToParent(parentUri))
            advanceUntilIdle()

            val updated =
                vm.uiState.value.feedItems
                    .first()
                    .leafPost()
            assertEquals(4, updated.stats.replyCount)
        }

    @Test
    fun `OnReplySubmittedToParent is a no-op when the parent isn't in the loaded slice`() =
        runTest(mainDispatcher.dispatcher) {
            // Common case when the user replies from a non-feed surface
            // (e.g. post-detail thread → composer → submit), or when
            // the parent was paginated off the loaded slice between
            // composer-open and submit-success. No crash, no spurious
            // state change.
            val parent = samplePost("at://did:plc:author/app.bsky.feed.post/p1")
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(persistentListOf(FeedItemUi.Single(parent)), null),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            val before = vm.uiState.value

            vm.handleEvent(
                FeedEvent.OnReplySubmittedToParent("at://did:plc:other/app.bsky.feed.post/missing"),
            )
            advanceUntilIdle()

            assertSame(before, vm.uiState.value)
        }

    // ---------- share tests ----------

    @Test
    fun `OnShareClicked emits SharePost with bsky_app permalink as the share text`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache())
            advanceUntilIdle()
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
            val vm = FeedViewModel(repo, FakePostInteractionsCache())
            advanceUntilIdle()
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
    fun `OnPostTapped emits NavigateToPost with the tapped post's URI`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache())
            advanceUntilIdle()
            val post = samplePost("at://did:plc:fake/app.bsky.feed.post/p1")

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnPostTapped(post))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToPost)
                assertEquals(post.id, (effect as FeedEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `OnAuthorTapped emits NavigateToAuthor with the author DID`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache())
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnAuthorTapped("did:plc:alice000000000000000000"))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToAuthor)
                assertEquals("did:plc:alice000000000000000000", (effect as FeedEffect.NavigateToAuthor).authorDid)
            }
        }

    // ---------- m28.4: page-boundary chain merge ----------

    @Test
    fun `LoadMore extends a Single tail into a chain when new page head links to it`() =
        runTest(mainDispatcher.dispatcher) {
            // Page 1: alice/1 (no reply). Page 2: alice/2 (reply to alice/1, same author).
            val page1 =
                chainTimelinePage(
                    cursor = "c1",
                    chainEntries(
                        ChainEntrySpec(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                    ),
                )
            val page2 =
                chainTimelinePage(
                    cursor = null,
                    chainEntries(
                        ChainEntrySpec(
                            uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                            authorDid = "did:plc:alice000000000000000000",
                            replyParent = ParentRef("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                        ),
                    ),
                )
            val repo = FakeFeedRepository(pages = listOf(Result.success(page1), Result.success(page2)))
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(FeedEvent.LoadMore)
            advanceUntilIdle()

            val items = vm.uiState.value.feedItems
            assertEquals(1, items.size, "tail merged with new page head into one chain")
            val chain = items.single()
            assertTrue(chain is FeedItemUi.SelfThreadChain, "expected SelfThreadChain, got $chain")
            chain as FeedItemUi.SelfThreadChain
            assertEquals(2, chain.posts.size)
            assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", chain.posts[0].id)
            assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/2", chain.posts[1].id)
        }

    @Test
    fun `LoadMore appends new page as-is when head does not link to existing tail`() =
        runTest(mainDispatcher.dispatcher) {
            val page1 =
                chainTimelinePage(
                    cursor = "c1",
                    chainEntries(
                        ChainEntrySpec(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                    ),
                )
            val page2 =
                chainTimelinePage(
                    cursor = null,
                    chainEntries(
                        ChainEntrySpec(uri = "at://did:plc:bob00000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:bob00000000000000000000"),
                    ),
                )
            val repo = FakeFeedRepository(pages = listOf(Result.success(page1), Result.success(page2)))
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(FeedEvent.LoadMore)
            advanceUntilIdle()

            val items = vm.uiState.value.feedItems
            assertEquals(2, items.size)
            assertTrue(items[0] is FeedItemUi.Single)
            assertTrue(items[1] is FeedItemUi.Single)
        }

    @Test
    fun `LoadMore extends an existing SelfThreadChain tail with a linked head`() =
        runTest(mainDispatcher.dispatcher) {
            // Page 1 forms a chain of 2; page 2 extends it with a third post.
            val page1 =
                chainTimelinePage(
                    cursor = "c1",
                    chainEntries(
                        ChainEntrySpec(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                        ChainEntrySpec(
                            uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                            authorDid = "did:plc:alice000000000000000000",
                            replyParent = ParentRef("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                        ),
                    ),
                )
            val page2 =
                chainTimelinePage(
                    cursor = null,
                    chainEntries(
                        ChainEntrySpec(
                            uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/3",
                            authorDid = "did:plc:alice000000000000000000",
                            replyParent = ParentRef("at://did:plc:alice000000000000000000/app.bsky.feed.post/2", "did:plc:alice000000000000000000"),
                        ),
                    ),
                )
            val repo = FakeFeedRepository(pages = listOf(Result.success(page1), Result.success(page2)))
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(FeedEvent.LoadMore)
            advanceUntilIdle()

            val items = vm.uiState.value.feedItems
            assertEquals(1, items.size)
            val chain = items.single() as FeedItemUi.SelfThreadChain
            assertEquals(3, chain.posts.size)
            assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/3", chain.posts.last().id)
        }

    @Test
    fun `LoadMore extends a chain across cursor-resync overlap (server replays the existing tail)`() =
        runTest(mainDispatcher.dispatcher) {
            // Page 1 ends on alice/1. Page 2's first wire entry is alice/1
            // again (server resync overlap), then the actual successor
            // alice/2. The boundary merge MUST skip the duplicate and
            // run the link check against alice/2, otherwise the chain
            // would render visually split.
            val page1 =
                chainTimelinePage(
                    cursor = "c1",
                    chainEntries(
                        ChainEntrySpec(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                    ),
                )
            val page2 =
                chainTimelinePage(
                    cursor = null,
                    chainEntries(
                        // Overlap: server replays alice/1 as the first entry.
                        ChainEntrySpec(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                        ChainEntrySpec(
                            uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                            authorDid = "did:plc:alice000000000000000000",
                            replyParent = ParentRef("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                        ),
                    ),
                )
            val repo = FakeFeedRepository(pages = listOf(Result.success(page1), Result.success(page2)))
            val vm = FeedViewModel(repo, FakePostInteractionsCache())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(FeedEvent.LoadMore)
            advanceUntilIdle()

            val items = vm.uiState.value.feedItems
            assertEquals(1, items.size, "tail merged across the resync overlap into one chain")
            val chain = items.single() as FeedItemUi.SelfThreadChain
            assertEquals(2, chain.posts.size)
            assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", chain.posts[0].id)
            assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/2", chain.posts[1].id)
        }
}

// ---------- m28.4: chain-merge fixture helpers ----------

private data class ParentRef(
    val uri: String,
    val authorDid: String,
)

private data class ChainEntrySpec(
    val uri: String,
    val authorDid: String,
    val replyParent: ParentRef? = null,
    val reposterDid: String? = null,
)

private fun chainEntries(vararg specs: ChainEntrySpec): List<ChainEntrySpec> = specs.toList()

/**
 * Decodes a `TimelinePage` from a list of entry specs, populating both
 * `feedItems` (chain-projected via `toFeedItemsUi`) and `wirePosts`
 * (raw `FeedViewPost` list, required by the VM's page-boundary merge
 * to read `reply.parent.uri` on the new-page head).
 */
private fun chainTimelinePage(
    cursor: String?,
    specs: List<ChainEntrySpec>,
): TimelinePage {
    val payload =
        """
        { "feed": [${specs.joinToString(",") { it.toJson() }}] }
        """.trimIndent()
    val response =
        kotlinx.serialization.json
            .Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }.decodeFromString(
                GetTimelineResponse
                    .serializer(),
                payload,
            )
    return TimelinePage(
        feedItems = response.feed.toFeedItemsUi(),
        nextCursor = cursor,
        wirePosts = response.feed.toImmutableList(),
    )
}

private fun ChainEntrySpec.toJson(): String {
    val replyBlock =
        if (replyParent == null) {
            ""
        } else {
            """
            "reply": {
              "root": {
                "${'$'}type": "app.bsky.feed.defs#postView",
                "uri": "${replyParent.uri}",
                "cid": "bafyreifakecid000000000000000000000000000000000",
                "author": { "did": "${replyParent.authorDid}", "handle": "fake.bsky.social" },
                "indexedAt": "2026-04-26T12:00:00Z",
                "record": { "${'$'}type": "app.bsky.feed.post", "text": "parent", "createdAt": "2026-04-26T12:00:00Z" }
              },
              "parent": {
                "${'$'}type": "app.bsky.feed.defs#postView",
                "uri": "${replyParent.uri}",
                "cid": "bafyreifakecid000000000000000000000000000000000",
                "author": { "did": "${replyParent.authorDid}", "handle": "fake.bsky.social" },
                "indexedAt": "2026-04-26T12:00:00Z",
                "record": { "${'$'}type": "app.bsky.feed.post", "text": "parent", "createdAt": "2026-04-26T12:00:00Z" }
              }
            },
            """.trimIndent()
        }
    val reasonBlock =
        if (reposterDid == null) {
            ""
        } else {
            """
            "reason": {
              "${'$'}type": "app.bsky.feed.defs#reasonRepost",
              "by": { "did": "$reposterDid", "handle": "reposter.bsky.social" },
              "indexedAt": "2026-04-26T12:00:00Z"
            },
            """.trimIndent()
        }
    return """
        {
          "post": {
            "uri": "$uri",
            "cid": "bafyreifakecid000000000000000000000000000000000",
            "author": { "did": "$authorDid", "handle": "fake.bsky.social" },
            "indexedAt": "2026-04-26T12:00:00Z",
            "record": { "${'$'}type": "app.bsky.feed.post", "text": "post text $uri", "createdAt": "2026-04-26T12:00:00Z" }
          },
          $replyBlock
          $reasonBlock
          "indexedAt": "2026-04-26T12:00:00Z"
        }
        """.trimIndent()
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
 * Convenience accessor used by the reply-count tests — the timeline
 * fixtures register a single Single feed entry, so `first().leafPost()`
 * returns the post under test regardless of the FeedItemUi shape.
 */
private fun FeedItemUi.leafPost(): PostUi =
    when (this) {
        is FeedItemUi.Single -> post
        is FeedItemUi.ReplyCluster -> leaf
        is FeedItemUi.SelfThreadChain -> posts.last()
    }
