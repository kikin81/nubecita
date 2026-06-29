package net.kikin.nubecita.feature.feed.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.FeedType
import net.kikin.nubecita.core.analytics.ViewFeed
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import net.kikin.nubecita.feature.feed.impl.data.toFeedItemsUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    // SharedVideoPlayer is process-scoped; a relaxed mock satisfies the
    // constructor without affecting the state/event tests here.
    private val sharedVideoPlayer: SharedVideoPlayer = mockk(relaxed = true)

    // Recording analytics sink so emission tests can assert the exact
    // typed events fired at the view_feed / interact_post call sites.
    private val analytics = RecordingAnalyticsClient()

    // No-op mute repo shared by tests that don't exercise mute/unmute.
    private val noOpMuteRepo = FakeMuteRepository()

    @Test
    fun `initial Load success populates posts, advances cursor, returns to Idle`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1", "p2"), nextCursor = "c1"))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
    fun `unbound VM defaults to getTimeline (Following) on Load`() =
        // Pin: a pane that never receives an explicit Bind (the pre-host
        // FeedScreen) must still load the Following timeline.
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(listOf("getTimeline"), repo.calls.map { it.method })
        }

    @Test
    fun `a fresh VM starts in InitialLoading so the shimmer shows from the first frame`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(feedItems("p1"), null))))
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            // Cold-start regression guard (lq9t.3.6): before any fetch resolves,
            // the status must be InitialLoading (shimmer), never Idle (which
            // projects to the "No posts" empty screen).
            assertEquals(FeedLoadStatus.InitialLoading, vm.uiState.value.loadStatus)
        }

    @Test
    fun `a real feed-switch Bind starts the fetch itself without a separate Load`() =
        runTest(mainDispatcher.dispatcher) {
            // A non-default Bind (feed switch) must drive the fetch itself so the
            // cold-start Bind-vs-Load effect ordering can't strand the feed on an
            // empty shimmer (if Load ran first and bind() then cancelled it).
            val repo = FakeFeedRepository(pages = listOf(Result.success(TimelinePage(feedItems("p1"), null))))
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator))
            advanceUntilIdle()

            assertEquals(1, repo.calls.size)
            assertEquals("getFeed", repo.calls.single().method)
            assertEquals(FeedLoadStatus.Idle, vm.uiState.value.loadStatus)
        }

    @Test
    fun `Bind Following dispatches Load to getTimeline`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "", kind = FeedKind.Following))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(1, repo.calls.size)
            val call = repo.calls.single()
            assertEquals("getTimeline", call.method)
            assertNull(call.cursor)
        }

    @Test
    fun `Bind Generator dispatches Load to getFeed with feedUri and never getTimeline`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(1, repo.calls.size)
            val call = repo.calls.single()
            assertEquals("getFeed", call.method)
            assertEquals("at://did:plc:gen/app.bsky.feed.generator/art", call.feedUri)
            assertNull(call.cursor)
            assertTrue(repo.calls.none { it.method == "getTimeline" })
        }

    @Test
    fun `Bind List dispatches Load to getListFeed with listUri`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:owner/app.bsky.graph.list/friends", kind = FeedKind.List))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(1, repo.calls.size)
            val call = repo.calls.single()
            assertEquals("getListFeed", call.method)
            assertEquals("at://did:plc:owner/app.bsky.graph.list/friends", call.feedUri)
        }

    @Test
    fun `Bind List getListFeed failure populates InitialError (dispatch-by-kind failure symmetry)`() =
        // Pin: the List-bound failure path mirrors the getFeed/getTimeline
        // failure tests — a Result.failure from getListFeed flows into
        // InitialError, and the load dispatched on the List kind
        // (getListFeed), never getTimeline.
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository(pages = listOf(Result.failure(IOException("list feed down"))))
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:owner/app.bsky.graph.list/friends", kind = FeedKind.List))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is FeedLoadStatus.InitialError, "expected InitialError, got $status")
            assertEquals(FeedError.Network, (status as FeedLoadStatus.InitialError).error)
            assertTrue(
                vm.uiState.value.feedItems
                    .isEmpty(),
            )
            assertEquals(listOf("getListFeed"), repo.calls.map { it.method })
        }

    @Test
    fun `Refresh on a Generator-bound VM dispatches to getFeed (dispatch-by-kind symmetry)`() =
        // Pin: Refresh dispatches on the bound kind, just like Load —
        // getFeed for a Generator binding, never getTimeline.
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(TimelinePage(feedItems = feedItems("g1"), nextCursor = "gc1")),
                            Result.success(TimelinePage(feedItems = feedItems("g2"), nextCursor = "gc2")),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(FeedEvent.Refresh)
            advanceUntilIdle()

            assertEquals(
                listOf("g2"),
                vm.uiState.value.feedItems
                    .map { it.key },
            )
            assertEquals("gc2", vm.uiState.value.nextCursor)
            assertEquals(listOf("getFeed", "getFeed"), repo.calls.map { it.method })
            assertTrue(repo.calls.none { it.method == "getTimeline" })
            // Refresh carried a null cursor (head re-fetch).
            assertNull(repo.calls[1].cursor)
        }

    @Test
    fun `Generator pagination advances cursor only on successful append, via getFeed`() =
        // Pin: pagination semantics are identical across kinds — cursor
        // advances on a successful append, and the append dispatches to
        // the bound kind (getFeed here, not getTimeline).
        runTest(mainDispatcher.dispatcher) {
            val page1 = TimelinePage(feedItems = feedItems("p1", "p2"), nextCursor = "c1")
            val page2 = TimelinePage(feedItems = feedItems("p3", "p4"), nextCursor = "c2")
            val repo = FakeFeedRepository(pages = listOf(Result.success(page1), Result.success(page2)))
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            assertEquals("c1", vm.uiState.value.nextCursor)

            vm.handleEvent(FeedEvent.LoadMore)
            advanceUntilIdle()

            assertEquals("c2", vm.uiState.value.nextCursor)
            assertEquals(4, vm.uiState.value.feedItems.size)
            assertEquals(listOf("getFeed", "getFeed"), repo.calls.map { it.method })
            // Append carried the first page's cursor.
            assertEquals("c1", repo.calls[1].cursor)
        }

    @Test
    fun `re-Bind to a different feed resets the loaded slice`() =
        runTest(mainDispatcher.dispatcher) {
            val following = TimelinePage(feedItems = feedItems("f1", "f2"), nextCursor = "fc")
            val generator = TimelinePage(feedItems = feedItems("g1"), nextCursor = null)
            val repo = FakeFeedRepository(pages = listOf(Result.success(following), Result.success(generator)))
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "", kind = FeedKind.Following))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.feedItems.size)

            // Rebinding to a generator clears the Following slice so the
            // next Load fetches the generator fresh instead of being
            // short-circuited by the "feedItems already present" guard.
            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator))
            assertTrue(
                vm.uiState.value.feedItems
                    .isEmpty(),
            )
            assertNull(vm.uiState.value.nextCursor)

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.feedItems.size)
            assertEquals(listOf("getTimeline", "getFeed"), repo.calls.map { it.method })
        }

    @Test
    fun `re-Bind to the same feed is a no-op and preserves the loaded slice`() =
        // Pin: the host re-emits Bind on every recomposition of the pane's
        // LaunchedEffect(feedUri); an idempotent same-pair bind must not
        // wipe a loaded feed on tab re-entry.
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("g1", "g2"), nextCursor = "gc"))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            val loaded = vm.uiState.value

            vm.handleEvent(FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator))

            assertSame(loaded, vm.uiState.value)
            assertEquals(1, repo.calls.size)
        }

    @Test
    fun `re-Bind cancels an in-flight load so the previous feed's page can't land in the new pane`() =
        // Pin (.6 review): bind(A) → Load suspends inside getTimeline →
        // bind(B) cancels the in-flight job and resets the slice → Load on
        // B fetches B fresh. When the gated A-fetch is finally released its
        // result must NOT write into the rebound pane, and the
        // feedItems.isNotEmpty() guard must not short-circuit B's load.
        runTest(mainDispatcher.dispatcher) {
            val followingGate = CompletableDeferred<Result<TimelinePage>>()
            val generatorPage =
                Result.success(TimelinePage(feedItems = feedItems("g1", "g2"), nextCursor = "gc"))
            val repo =
                FakeFeedRepository(
                    pageProducer = { method, _, _ ->
                        when (method) {
                            // Following (feed A) suspends until the test releases it.
                            "getTimeline" -> followingGate.await()
                            // Generator (feed B) resolves immediately.
                            else -> generatorPage
                        }
                    },
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            // Bind A (Following) and start its load; it suspends on the gate.
            vm.handleEvent(FeedEvent.Bind(feedUri = "", kind = FeedKind.Following))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            assertTrue(
                vm.uiState.value.feedItems
                    .isEmpty(),
                "A's fetch is still suspended",
            )
            assertEquals(FeedLoadStatus.InitialLoading, vm.uiState.value.loadStatus)

            // Re-bind to B (Generator) while A's fetch is in flight, then load B.
            vm.handleEvent(
                FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator),
            )
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            // B's page landed.
            assertEquals(
                listOf("g1", "g2"),
                vm.uiState.value.feedItems
                    .map { it.key },
            )
            assertEquals(FeedLoadStatus.Idle, vm.uiState.value.loadStatus)

            // Now release A's gated fetch. Its continuation was cancelled by the
            // re-bind, so the stale Following page must NOT overwrite B's slice.
            followingGate.complete(
                Result.success(TimelinePage(feedItems = feedItems("f1", "f2", "f3"), nextCursor = "fc")),
            )
            advanceUntilIdle()

            assertEquals(
                listOf("g1", "g2"),
                vm.uiState.value.feedItems
                    .map { it.key },
                "stale Following page must not land after re-bind",
            )
            assertEquals("gc", vm.uiState.value.nextCursor)
            assertEquals(FeedLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals(listOf("getTimeline", "getFeed"), repo.calls.map { it.method })
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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
                    pageProducer = { _, _, _ ->
                        first.await()
                    },
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val repo = FakeFeedRepository(pageProducer = { _, _, _ -> first.await() })
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
                    pageProducer = { _, _, _ ->
                        when (call++) {
                            0 -> initial // initial Load
                            else -> refreshDeferred.await() // first Refresh
                        }
                    },
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
                    pageProducer = { _, _, _ ->
                        when (call++) {
                            0 -> initial
                            else -> refreshDeferred.await()
                        }
                    },
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
                    pageProducer = { _, _, _ ->
                        when (call++) {
                            0 -> initial
                            else -> appendDeferred.await()
                        }
                    },
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            val post = samplePost(id = "at://post-a", cid = "bafyA")

            vm.onLike(post)
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
            val vm = FeedViewModel(FakeFeedRepository(), cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
            advanceUntilIdle()

            vm.effects.test {
                vm.onLike(samplePost(id = "at://post-x", cid = "bafyX"))
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
            val vm = FeedViewModel(repo, cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
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
    fun `cache state emission projects onto leaf post inside ReplyCluster`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val rootId = "at://did:plc:alice/app.bsky.feed.post/root"
            val parentId = "at://did:plc:alice/app.bsky.feed.post/parent"
            val leafId = "at://did:plc:alice/app.bsky.feed.post/leaf"
            val cluster =
                FeedItemUi.ReplyCluster(
                    root = samplePost(id = rootId),
                    parent = samplePost(id = parentId),
                    leaf = samplePost(id = leafId),
                    hasEllipsis = false,
                )
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(persistentListOf(cluster), null))),
                )
            val vm = FeedViewModel(repo, cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            cache.emit(
                persistentMapOf(
                    leafId to
                        PostInteractionState(
                            viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/rc1",
                            likeCount = 7L,
                        ),
                ),
            )
            advanceUntilIdle()

            val mergedCluster =
                vm.uiState.value.feedItems
                    .filterIsInstance<FeedItemUi.ReplyCluster>()
                    .first { it.leaf.id == leafId }
            assertTrue(mergedCluster.leaf.viewer.isLikedByViewer)
            assertEquals(7, mergedCluster.leaf.stats.likeCount)
            // Root and parent are unchanged — reference equality preserved.
            assertSame(cluster.root, mergedCluster.root)
            assertSame(cluster.parent, mergedCluster.parent)
        }

    @Test
    fun `cache state emission projects onto posts inside SelfThreadChain`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val firstId = "at://did:plc:alice/app.bsky.feed.post/chain1"
            val lastId = "at://did:plc:alice/app.bsky.feed.post/chain2"
            val chain =
                FeedItemUi.SelfThreadChain(
                    posts = persistentListOf(samplePost(id = firstId), samplePost(id = lastId)),
                )
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(persistentListOf(chain), null))),
                )
            val vm = FeedViewModel(repo, cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            cache.emit(
                persistentMapOf(
                    lastId to
                        PostInteractionState(
                            viewerRepostUri = "at://did:plc:viewer/app.bsky.feed.repost/sc1",
                            repostCount = 5L,
                        ),
                ),
            )
            advanceUntilIdle()

            val mergedChain =
                vm.uiState.value.feedItems
                    .filterIsInstance<FeedItemUi.SelfThreadChain>()
                    .first { it.posts.last().id == lastId }
            assertTrue(
                mergedChain.posts
                    .last()
                    .viewer.isRepostedByViewer,
            )
            assertEquals(
                5,
                mergedChain.posts
                    .last()
                    .stats.repostCount,
            )
            // First post is unchanged — reference equality preserved.
            assertSame(chain.posts.first(), mergedChain.posts.first())
        }

    @Test
    fun `OnRepostClicked dispatches cache toggleRepost with post id and cid`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("at://post-b"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            val post = samplePost(id = "at://post-b", cid = "bafyB")

            vm.onRepost(post)
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
            val vm = FeedViewModel(FakeFeedRepository(), cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
            advanceUntilIdle()

            vm.effects.test {
                vm.onRepost(samplePost(id = "at://post-y", cid = "bafyY"))
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
            val vm = FeedViewModel(repo, cache, sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler(cache))
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

    // ---------- analytics emission tests (nubecita-049f.3) ----------

    @Test
    fun `successful initial Load emits view_feed(following) exactly once`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1", "p2"), nextCursor = "c1"))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(listOf(ViewFeed(FeedType.Following)), analytics.events)
        }

    @Test
    fun `failed initial Load does not emit view_feed`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository(pages = listOf(Result.failure(IOException("down"))))
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertTrue(analytics.events.isEmpty(), "no view_feed on a failed load")
        }

    @Test
    fun `Refresh does not re-emit view_feed (only initial load counts as a feed view)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = "c1")),
                            Result.success(TimelinePage(feedItems = feedItems("p2"), nextCursor = "c2")),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(FeedEvent.Refresh)
            advanceUntilIdle()

            assertEquals(listOf(ViewFeed(FeedType.Following)), analytics.events)
        }

    @Test
    fun `initial Load on the Discover generator emits view_feed(discover)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(FeedEvent.Bind(feedUri = PinnedFeedsRepository.DISCOVER_FEED_URI, kind = FeedKind.Generator))
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(listOf(ViewFeed(FeedType.Discover)), analytics.events)
        }

    @Test
    fun `initial Load on a non-Discover generator emits view_feed(custom)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(
                FeedEvent.Bind(feedUri = "at://did:plc:gen/app.bsky.feed.generator/art", kind = FeedKind.Generator),
            )
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(listOf(ViewFeed(FeedType.Custom)), analytics.events)
        }

    @Test
    fun `initial Load on a list emits view_feed(list)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeFeedRepository(
                    pages = listOf(Result.success(TimelinePage(feedItems = feedItems("p1"), nextCursor = null))),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

            vm.handleEvent(
                FeedEvent.Bind(feedUri = "at://did:plc:owner/app.bsky.graph.list/friends", kind = FeedKind.List),
            )
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            assertEquals(listOf(ViewFeed(FeedType.List)), analytics.events)
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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val before = vm.uiState.value
            val post =
                samplePost("p1").copy(
                    id = "at://did:plc:fake/app.bsky.feed.post/3krkey1",
                )

            vm.effects.test {
                vm.onShare(post)

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val post =
                samplePost("p1").copy(
                    id = "at://did:plc:fake/app.bsky.feed.post/3krkey9",
                )

            vm.effects.test {
                vm.onShareLongPress(post)

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
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
    fun `OnImageTapped emits NavigateToMediaViewer with the post URI and image index`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val post = samplePost("at://did:plc:fake/app.bsky.feed.post/p1")

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnImageTapped(post = post, imageIndex = 2))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToMediaViewer)
                effect as FeedEffect.NavigateToMediaViewer
                assertEquals(post.id, effect.postUri)
                assertEquals(2, effect.imageIndex)
            }
        }

    @Test
    fun `OnQuotedPostTapped emits NavigateToPost with the quoted post's URI`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val quotedUri = "at://did:plc:other/app.bsky.feed.post/q1"

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnQuotedPostTapped(quotedUri))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToPost)
                assertEquals(quotedUri, (effect as FeedEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `OnVideoTapped emits NavigateToVideoPlayer with the tapped post's URI`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val videoUri = "at://did:plc:abc/app.bsky.feed.post/3v1d"

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnVideoTapped(videoUri))

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateToVideoPlayer)
                assertEquals(videoUri, (effect as FeedEffect.NavigateToVideoPlayer).postUri)
            }
        }

    // ---------- oftc.2 overflow-menu tests ----------

    @Test
    fun `OnOverflowAction emits ShowComingSoon for every action except ReportPost, BlockAuthor, MuteAuthor, UnmuteAuthor`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val post = samplePost("at://did:plc:fake/app.bsky.feed.post/over1")

            // The remaining stubbed overflow variants still pass through
            // as ShowComingSoon. ReportPost (oftc.3) and BlockAuthor
            // (oftc.16) have graduated to NavigateTo; MuteAuthor /
            // UnmuteAuthor (oftc.5) have graduated to real mute logic.
            val variants =
                listOf(
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.UnblockAuthor,
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteThread,
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteThread,
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.CopyPostText,
                )

            vm.effects.test {
                for (action in variants) {
                    vm.handleEvent(FeedEvent.OnOverflowAction(post = post, action = action))
                    val effect = awaitItem()
                    assertTrue(
                        effect is FeedEffect.ShowComingSoon,
                        "expected ShowComingSoon, got $effect (variant=$action)",
                    )
                    assertEquals(action, (effect as FeedEffect.ShowComingSoon).action)
                }
            }
        }

    @Test
    fun `OnOverflowAction(ReportPost) emits NavigateTo with a Report Post NavKey`() =
        // Pin: oftc.3 graduates the Report overflow row out of the
        // ShowComingSoon stub. The VM emits exactly one
        // FeedEffect.NavigateTo carrying a Report(ReportSubject.Post(...))
        // whose uri + cid match the tapped post — the screen-side
        // collector pushes the NavKey onto LocalMainShellNavState. No
        // state field changes (the post list, cursor, and load status
        // are untouched), and no ShowComingSoon / ShowError races into
        // the channel.
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val post =
                samplePost(
                    id = "at://did:plc:author/app.bsky.feed.post/rprt1",
                    cid = "bafyreitestreportcid",
                )

            val stateBefore = vm.uiState.value
            vm.effects.test {
                vm.handleEvent(
                    FeedEvent.OnOverflowAction(
                        post = post,
                        action = net.kikin.nubecita.designsystem.component.PostOverflowAction.ReportPost,
                    ),
                )

                val effect = awaitItem()
                assertTrue(
                    effect is FeedEffect.NavigateTo,
                    "expected NavigateTo, got $effect",
                )
                val key = (effect as FeedEffect.NavigateTo).key
                assertTrue(
                    key is net.kikin.nubecita.feature.moderation.api.Report,
                    "expected Report NavKey, got $key",
                )
                val subject = (key as net.kikin.nubecita.feature.moderation.api.Report).subject
                assertTrue(
                    subject is net.kikin.nubecita.feature.moderation.api.ReportSubject.Post,
                    "expected ReportSubject.Post, got $subject",
                )
                assertEquals(
                    post.id,
                    (subject as net.kikin.nubecita.feature.moderation.api.ReportSubject.Post).uri,
                )
                assertEquals(post.cid, subject.cid)
            }
            // Sticky state must not have moved — no spurious feedItems /
            // cursor / loadStatus mutation as a side effect.
            assertSame(stateBefore, vm.uiState.value)
        }

    @Test
    fun `OnOverflowAction(BlockAuthor) emits NavigateTo with a Block NavKey for the author`() =
        // Pin: oftc.16 graduates the Block overflow row to NavigateTo —
        // the VM emits FeedEffect.NavigateTo(Block.forAccount(did, handle))
        // for the tapped post's author; the screen collector pushes it onto
        // the nav stack where ModerationNavigationModule resolves the dialog.
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
            advanceUntilIdle()
            val post = samplePost("at://did:plc:fake/app.bsky.feed.post/blk1")

            val stateBefore = vm.uiState.value
            vm.effects.test {
                vm.handleEvent(
                    FeedEvent.OnOverflowAction(
                        post = post,
                        action = net.kikin.nubecita.designsystem.component.PostOverflowAction.BlockAuthor,
                    ),
                )
                val effect = awaitItem()
                assertTrue(effect is FeedEffect.NavigateTo, "expected NavigateTo, got $effect")
                val key = (effect as FeedEffect.NavigateTo).key
                assertTrue(
                    key is net.kikin.nubecita.feature.moderation.api.Block,
                    "expected Block NavKey, got $key",
                )
                key as net.kikin.nubecita.feature.moderation.api.Block
                assertEquals(post.author.did, key.did)
                assertEquals(post.author.handle, key.handle)
            }
            assertSame(stateBefore, vm.uiState.value)
        }

    @Test
    fun `OnAuthorTapped emits NavigateToAuthor with the author DID`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeFeedRepository()
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())
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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, noOpMuteRepo, FakePostInteractionHandler())

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

    // ---------- oftc.5: mute / unmute ----------

    @Test
    fun `OnOverflowAction(MuteAuthor) removes the muted author's posts and calls muteActor`() =
        // Pin (a): MuteAuthor resolves the author DID, immediately removes
        // every feed item whose primary post is by that author, and calls
        // muteRepository.muteActor(did). On success the removal is permanent
        // (posts stay hidden until the next full feed refresh populates a new
        // page from the server, which already applies the mute filter).
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:muted000000000000000000"
            val muteRepo = FakeMuteRepository()
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems =
                                        persistentListOf(
                                            FeedItemUi.Single(samplePost("at://post-muted-1", authorDid = authorDid)),
                                            FeedItemUi.Single(samplePost("at://post-other", authorDid = "did:plc:other")),
                                            FeedItemUi.Single(samplePost("at://post-muted-2", authorDid = authorDid)),
                                        ),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val post = samplePost("at://post-muted-1", authorDid = authorDid)
            vm.handleEvent(FeedEvent.OnOverflowAction(post = post, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor))
            advanceUntilIdle()

            // Only the non-muted post remains.
            val items = vm.uiState.value.feedItems
            assertEquals(1, items.size, "muted author's posts must be removed")
            assertEquals("at://post-other", (items.first() as FeedItemUi.Single).post.id)

            // muteActor called with the correct DID.
            assertEquals(1, muteRepo.muteActorCalls.size)
            assertEquals(authorDid, muteRepo.muteActorCalls.first())
        }

    @Test
    fun `OnOverflowAction(MuteAuthor) failure restores removed posts and emits ShowError`() =
        // Pin (b): when muteActor returns a failure, the optimistically-removed
        // posts are put back and a ShowError effect is emitted so the screen
        // can show a snackbar.
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:muted000000000000000000"
            val muteRepo =
                FakeMuteRepository().apply {
                    nextMuteResult = Result.failure(java.io.IOException("network error"))
                }
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems =
                                        persistentListOf(
                                            FeedItemUi.Single(samplePost("at://post-muted-1", authorDid = authorDid)),
                                            FeedItemUi.Single(samplePost("at://post-other", authorDid = "did:plc:other")),
                                        ),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val post = samplePost("at://post-muted-1", authorDid = authorDid)
            vm.effects.test {
                vm.handleEvent(FeedEvent.OnOverflowAction(post = post, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError, "expected ShowError on mute failure, got $effect")
                cancelAndIgnoreRemainingEvents()
            }

            // Both posts are restored after the failure rollback.
            assertEquals(2, vm.uiState.value.feedItems.size, "removed posts must be restored on failure")
        }

    @Test
    fun `OnOverflowAction(UnmuteAuthor) flips isAuthorMutedByViewer to false and calls unmuteActor`() =
        // Pin (c): UnmuteAuthor optimistically flips the mute flag to false on
        // all posts by that author visible in the current feed slice, then calls
        // unmuteActor(did). On success the flag stays false.
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:muted000000000000000000"
            val muteRepo = FakeMuteRepository()
            val mutedViewer = ViewerStateUi(isAuthorMutedByViewer = true)
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems =
                                        persistentListOf(
                                            FeedItemUi.Single(samplePost("at://post-muted-1", authorDid = authorDid, viewer = mutedViewer)),
                                        ),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val post = samplePost("at://post-muted-1", authorDid = authorDid, viewer = mutedViewer)
            vm.handleEvent(FeedEvent.OnOverflowAction(post = post, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteAuthor))
            advanceUntilIdle()

            // unmuteActor called with the correct DID.
            assertEquals(1, muteRepo.unmuteActorCalls.size)
            assertEquals(authorDid, muteRepo.unmuteActorCalls.first())

            // Flag flipped to false on the post.
            val item =
                vm.uiState.value.feedItems
                    .first() as FeedItemUi.Single
            assertFalse(item.post.viewer.isAuthorMutedByViewer, "isAuthorMutedByViewer must be false after unmute")
        }

    @Test
    fun `OnOverflowAction(UnmuteAuthor) failure rolls back isAuthorMutedByViewer and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:muted000000000000000000"
            val muteRepo =
                FakeMuteRepository().apply {
                    nextUnmuteResult = Result.failure(java.io.IOException("network error"))
                }
            val mutedViewer = ViewerStateUi(isAuthorMutedByViewer = true)
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems =
                                        persistentListOf(
                                            FeedItemUi.Single(samplePost("at://post-muted-1", authorDid = authorDid, viewer = mutedViewer)),
                                        ),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val post = samplePost("at://post-muted-1", authorDid = authorDid, viewer = mutedViewer)
            vm.effects.test {
                vm.handleEvent(FeedEvent.OnOverflowAction(post = post, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteAuthor))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is FeedEffect.ShowError, "expected ShowError on unmute failure, got $effect")
                cancelAndIgnoreRemainingEvents()
            }

            // Flag rolled back to true after failure.
            val item =
                vm.uiState.value.feedItems
                    .first() as FeedItemUi.Single
            assertTrue(item.post.viewer.isAuthorMutedByViewer, "isAuthorMutedByViewer must be restored on failure")
        }

    // ---------- oftc.5 branch coverage: ReplyCluster + SelfThreadChain ----------

    @Test
    fun `MuteAuthor removes ReplyCluster whose leaf is by the muted author`() =
        // removeItemsByAuthor removes a ReplyCluster when its leaf post's author
        // matches the muted DID. The leaf is the "followed" post — muting its
        // author hides the entire cluster from the feed.
        runTest(mainDispatcher.dispatcher) {
            val mutedDid = "did:plc:muted000000000000000000"
            val otherDid = "did:plc:other000000000000000000"
            val muteRepo = FakeMuteRepository()
            val cluster =
                FeedItemUi.ReplyCluster(
                    root = samplePost("at://root", authorDid = otherDid),
                    parent = samplePost("at://parent", authorDid = otherDid),
                    leaf = samplePost("at://leaf-muted", authorDid = mutedDid),
                    hasEllipsis = false,
                )
            val unrelated = FeedItemUi.Single(samplePost("at://unrelated", authorDid = otherDid))
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems = persistentListOf(cluster, unrelated),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(
                FeedEvent.OnOverflowAction(
                    post = samplePost("at://leaf-muted", authorDid = mutedDid),
                    action = net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor,
                ),
            )
            advanceUntilIdle()

            val items = vm.uiState.value.feedItems
            assertEquals(1, items.size, "cluster whose leaf is by the muted author must be removed")
            assertEquals("at://unrelated", (items.first() as FeedItemUi.Single).post.id)
        }

    @Test
    fun `MuteAuthor keeps ReplyCluster whose leaf is by a non-muted author`() =
        // removeItemsByAuthor keeps a ReplyCluster when the leaf is NOT by
        // the muted author, even when root/parent are. The non-muted user's
        // reply is kept as conversation context.
        runTest(mainDispatcher.dispatcher) {
            val mutedDid = "did:plc:muted000000000000000000"
            val leafDid = "did:plc:other000000000000000000"
            val muteRepo = FakeMuteRepository()
            val cluster =
                FeedItemUi.ReplyCluster(
                    root = samplePost("at://root-muted", authorDid = mutedDid),
                    parent = samplePost("at://parent-muted", authorDid = mutedDid),
                    leaf = samplePost("at://leaf-other", authorDid = leafDid),
                    hasEllipsis = false,
                )
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems = persistentListOf(cluster),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            val loadedCluster =
                vm.uiState.value.feedItems
                    .first()
            vm.handleEvent(
                FeedEvent.OnOverflowAction(
                    post = samplePost("at://root-muted", authorDid = mutedDid),
                    action = net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor,
                ),
            )
            advanceUntilIdle()

            val items = vm.uiState.value.feedItems
            assertEquals(1, items.size, "cluster with non-muted leaf must be kept as context")
            // The cluster instance itself is preserved — filter kept the reference.
            assertSame(loadedCluster, items.first())
        }

    @Test
    fun `MuteAuthor removes SelfThreadChain whose posts are by the muted author`() =
        // removeItemsByAuthor removes a SelfThreadChain when any post is authored
        // by the muted DID. All posts in a chain share the same author by
        // construction, so the entire chain is removed.
        runTest(mainDispatcher.dispatcher) {
            val mutedDid = "did:plc:muted000000000000000000"
            val otherDid = "did:plc:other000000000000000000"
            val muteRepo = FakeMuteRepository()
            val chain =
                FeedItemUi.SelfThreadChain(
                    posts =
                        persistentListOf(
                            samplePost("at://chain-1", authorDid = mutedDid),
                            samplePost("at://chain-2", authorDid = mutedDid),
                        ),
                )
            val unrelated = FeedItemUi.Single(samplePost("at://unrelated", authorDid = otherDid))
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems = persistentListOf(chain, unrelated),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(
                FeedEvent.OnOverflowAction(
                    post = samplePost("at://chain-1", authorDid = mutedDid),
                    action = net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor,
                ),
            )
            advanceUntilIdle()

            val items = vm.uiState.value.feedItems
            assertEquals(1, items.size, "SelfThreadChain by the muted author must be removed")
            assertEquals("at://unrelated", (items.first() as FeedItemUi.Single).post.id)
        }

    @Test
    fun `UnmuteAuthor flips isAuthorMutedByViewer on ReplyCluster positions by muted author and preserves unchanged post references`() =
        // updateViewerStateByAuthor updates root, parent, and leaf independently
        // when the muted author appears at each position. Posts by a different
        // author are returned as the same reference (no copy), preserving
        // LazyColumn skip-recomposition for unchanged items.
        runTest(mainDispatcher.dispatcher) {
            val mutedDid = "did:plc:muted000000000000000000"
            val otherDid = "did:plc:other000000000000000000"
            val muteRepo = FakeMuteRepository()
            val mutedViewer = ViewerStateUi(isAuthorMutedByViewer = true)

            // root = muted, parent = muted; leaf = different author (not muted).
            val cluster =
                FeedItemUi.ReplyCluster(
                    root = samplePost("at://root", authorDid = mutedDid, viewer = mutedViewer),
                    parent = samplePost("at://parent", authorDid = mutedDid, viewer = mutedViewer),
                    leaf = samplePost("at://leaf", authorDid = otherDid),
                    hasEllipsis = false,
                )
            val repo =
                FakeFeedRepository(
                    pages =
                        listOf(
                            Result.success(
                                TimelinePage(
                                    feedItems = persistentListOf(cluster),
                                    nextCursor = null,
                                ),
                            ),
                        ),
                )
            val vm = FeedViewModel(repo, FakePostInteractionsCache(), sharedVideoPlayer, analytics, muteRepo, FakePostInteractionHandler())
            vm.handleEvent(FeedEvent.Load)
            advanceUntilIdle()

            // Capture the leaf reference from the loaded cluster before unmute.
            val loadedLeaf =
                (
                    vm.uiState.value.feedItems
                        .first() as FeedItemUi.ReplyCluster
                ).leaf

            vm.handleEvent(
                FeedEvent.OnOverflowAction(
                    post = samplePost("at://root", authorDid = mutedDid, viewer = mutedViewer),
                    action = net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteAuthor,
                ),
            )
            advanceUntilIdle()

            val updated =
                vm.uiState.value.feedItems
                    .first() as FeedItemUi.ReplyCluster
            assertFalse(updated.root.viewer.isAuthorMutedByViewer, "root flag must flip to false")
            assertFalse(updated.parent.viewer.isAuthorMutedByViewer, "parent flag must flip to false")
            // Leaf is by a different author — reference must be preserved (no copy).
            assertSame(loadedLeaf, updated.leaf, "leaf reference must be unchanged")
            assertEquals(1, muteRepo.unmuteActorCalls.size)
            assertEquals(mutedDid, muteRepo.unmuteActorCalls.first())
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
    authorDid: String = "did:plc:fake",
): PostUi =
    PostUi(
        id = id,
        cid = cid,
        author =
            AuthorUi(
                did = authorDid,
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
    private val pageProducer: (suspend (method: String, cursor: String?, limit: Int) -> Result<TimelinePage>)? = null,
) : FeedRepository {
    private val pageQueue = ArrayDeque(pages)

    /**
     * Every `getTimeline` / `getFeed` / `getListFeed` call, recorded in
     * order. `invocations` preserves the original `(cursor, limit)` shape
     * the existing Following tests assert against; `calls` adds the
     * dispatched method + target URI so the kind-dispatch tests can prove
     * the VM routed to the right repository method.
     */
    val invocations = mutableListOf<Pair<String?, Int>>()
    val calls = mutableListOf<Call>()

    data class Call(
        val method: String,
        val feedUri: String?,
        val cursor: String?,
        val limit: Int,
    )

    override suspend fun getTimeline(
        cursor: String?,
        limit: Int,
    ): Result<TimelinePage> {
        calls += Call("getTimeline", feedUri = null, cursor = cursor, limit = limit)
        return nextPage("getTimeline", cursor, limit)
    }

    override suspend fun getFeed(
        feedUri: String,
        cursor: String?,
        limit: Int,
    ): Result<TimelinePage> {
        calls += Call("getFeed", feedUri = feedUri, cursor = cursor, limit = limit)
        return nextPage("getFeed", cursor, limit)
    }

    override suspend fun getListFeed(
        listUri: String,
        cursor: String?,
        limit: Int,
    ): Result<TimelinePage> {
        calls += Call("getListFeed", feedUri = listUri, cursor = cursor, limit = limit)
        return nextPage("getListFeed", cursor, limit)
    }

    private suspend fun nextPage(
        method: String,
        cursor: String?,
        limit: Int,
    ): Result<TimelinePage> {
        invocations += cursor to limit
        return pageProducer?.invoke(method, cursor, limit)
            ?: pageQueue.removeFirstOrNull()
            ?: error("FakeFeedRepository got an unexpected $method call ($cursor, $limit)")
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
        // Tombstones never carry a post. Test fixtures never construct these
        // variants and then call leafPost(); the branch is here so the sealed
        // match stays exhaustive after oftc.6 added Blocked / NotFound.
        is FeedItemUi.Blocked, is FeedItemUi.NotFound ->
            error("leafPost called on tombstone $this")
    }
