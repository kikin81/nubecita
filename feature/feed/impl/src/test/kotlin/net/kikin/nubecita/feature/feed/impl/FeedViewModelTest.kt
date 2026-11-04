package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.FeedType
import net.kikin.nubecita.core.analytics.ViewFeed
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.feedcache.CatchUpOutcome
import net.kikin.nubecita.core.feedcache.FeedAnchor
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.posting.OutgoingPost
import net.kikin.nubecita.core.posting.OutgoingPostStatus
import net.kikin.nubecita.core.posting.PostDraft
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.preferences.AutoplayPolicy
import net.kikin.nubecita.core.preferences.AutoplayPreference
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
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.search.api.Hashtag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant
import net.kikin.nubecita.core.feedcache.FeedType as CacheFeedType

/**
 * The feed VM no longer fetches or accumulates a list — `:core:feed-cache`
 * does, and its own tests cover paging, de-duplication and the chain merge.
 *
 * What is left here, and what these tests are about: which cache partition a
 * binding reads, the three optimistic transforms over the paged data, the
 * outgoing-post rows, the analytics moment, and the error mapping the screen
 * renders. The paged output is asserted with `asSnapshot`, which collects a
 * `PagingData` the way the UI would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedViewModelTest {
    // Unconfined, not the extension's Standard default: `cachedIn` runs on
    // `viewModelScope` (Main), and `asSnapshot` has to see the cached
    // generation while it collects. Under a Standard dispatcher nothing drives
    // that scope while `asSnapshot` is suspended, and the collection never
    // settles — a hang, not a failure. `advanceUntilIdle` still works, since
    // both share the test scheduler.
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension(UnconfinedTestDispatcher())

    // SharedVideoPlayer is process-scoped; a relaxed mock satisfies the
    // constructor without affecting anything asserted here.
    private val sharedVideoPlayer: SharedVideoPlayer = mockk(relaxed = true)
    private val analytics = RecordingAnalyticsClient()
    private val noOpMuteRepo = FakeMuteRepository()
    private val outgoingStore = FakeOutgoingPostStore()
    private val session = FakeSessionStateProvider()

    private val created = mutableListOf<FeedViewModel>()

    /**
     * Runs a test and cancels every ViewModel scope it created before
     * `runTest` inspects the scheduler.
     *
     * `cachedIn(viewModelScope)` keeps a sharing coroutine alive for the
     * scope's lifetime. A real `ViewModel` loses that scope in `onCleared`;
     * a test has no such moment, so without this `runTest` waits out its
     * timeout and fails with `UncompletedCoroutinesError` — a timeout, not an
     * assertion, which is a slow and confusing way to learn nothing.
     */
    private fun feedTest(body: suspend TestScope.() -> Unit) =
        runTest(mainDispatcher.dispatcher) {
            try {
                body()
            } finally {
                created.forEach { it.viewModelScope.cancel() }
                created.clear()
            }
        }

    private fun viewModel(
        repository: FakeFeedRepository = FakeFeedRepository(),
        cache: FakePostInteractionsCache = FakePostInteractionsCache(),
        muteRepository: FakeMuteRepository = noOpMuteRepo,
        handler: FakePostInteractionHandler = FakePostInteractionHandler(),
        autoplayEnabled: Boolean = true,
    ): FeedViewModel =
        FeedViewModel(
            feedRepository = repository,
            sessionStateProvider = session,
            postInteractionsCache = cache,
            sharedVideoPlayer = sharedVideoPlayer,
            analytics = analytics,
            muteRepository = muteRepository,
            handler = handler,
            outgoingPostStore = outgoingStore,
            autoplayPolicy = autoplayPolicy(autoplayEnabled),
        ).also { created += it }

    // ---------- which partition a binding reads ----------

    @Test
    fun `an unbound VM reads the Following partition`() =
        feedTest {
            // A pane that never receives an explicit Bind — the pre-host
            // FeedScreen — must still show the home timeline.
            val repo = FakeFeedRepository()
            val vm = viewModel(repo)
            vm.feed.first()

            assertEquals(listOf(FeedKey.following(VIEWER)), repo.keys)
        }

    @Test
    fun `each feed kind maps to its own partition`() =
        feedTest {
            val repo = FakeFeedRepository()
            val vm = viewModel(repo)
            vm.feed.first()

            vm.handleEvent(FeedEvent.Bind(PinnedFeedsRepository.DISCOVER_FEED_URI, FeedKind.Generator))
            vm.feed.first()
            vm.handleEvent(FeedEvent.Bind("at://did:plc:gen/app.bsky.feed.generator/mine", FeedKind.Generator))
            vm.feed.first()
            vm.handleEvent(FeedEvent.Bind("at://did:plc:me/app.bsky.graph.list/friends", FeedKind.List))
            vm.feed.first()

            assertEquals(
                listOf(CacheFeedType.FOLLOWING, CacheFeedType.DISCOVER, CacheFeedType.CUSTOM, CacheFeedType.LIST),
                repo.keys.map { it.feedType },
            )
        }

    @Test
    fun `re-binding the same feed does not restart the pager`() =
        feedTest {
            // The host re-emits Bind from a LaunchedEffect on every
            // recomposition of the pane. Re-emitting the binding would restart
            // the Pager and throw away the reader's position — the opposite of
            // what this feature is for.
            val repo = FakeFeedRepository()
            val vm = viewModel(repo)
            vm.feed.first()

            vm.handleEvent(FeedEvent.Bind("", FeedKind.Following))
            vm.handleEvent(FeedEvent.Bind("", FeedKind.Following))
            vm.feed.first()

            assertEquals(1, repo.keys.size)
        }

    @Test
    fun `a signed-out session reads no partition at all`() =
        feedTest {
            // There is no "the feed" without an account: a partition is keyed
            // by DID, and falling back to a blank one would read another
            // account's rows.
            session.state.value = SessionState.SignedOut
            val repo = FakeFeedRepository()
            viewModel(repo)
            advanceUntilIdle()

            assertEquals(emptyList<FeedKey>(), repo.keys)
        }

    // ---------- the optimistic transforms ----------
    //
    // Driven through the extracted composition rather than `vm.feed`: that
    // flow is `cachedIn(viewModelScope)`, and `asSnapshot` cannot settle over a
    // scope the test has no way to complete. The VM's own tests below cover
    // what it does with these — which set it fills, and when it empties them.

    @Test
    fun `muting filters that author's items out`() =
        runTest {
            val items = listOf(single("at://mine", author = MUTED), single("at://theirs", author = OTHER))

            val kept = paged(items).withOptimisticState(mutes = setOf(MUTED)).asSnapshot()

            assertEquals(listOf("at://theirs"), kept.map { it.leafId })
        }

    @Test
    fun `an empty mute set changes nothing, and keeps item identity`() =
        runTest {
            // The common case. Preserving identity is what lets LazyColumn skip
            // recomposition for rows that did not change.
            val item = single("at://a")

            val kept = paged(listOf(item)).withOptimisticState().asSnapshot()

            assertSame(item, kept.single())
        }

    @Test
    fun `muting filters a cluster by its leaf's author, not its context`() =
        runTest {
            // The leaf is the followed post; root and parent are context. A
            // cluster whose leaf is someone else replying to the muted author
            // stays, which is what the server's next fetch does too.
            val items =
                listOf(
                    cluster(rootAuthor = OTHER, leafUri = "at://muted-leaf", leafAuthor = MUTED),
                    cluster(rootAuthor = MUTED, leafUri = "at://other-leaf", leafAuthor = OTHER),
                )

            val kept = paged(items).withOptimisticState(mutes = setOf(MUTED)).asSnapshot()

            assertEquals(listOf("at://other-leaf"), kept.map { it.leafId })
        }

    @Test
    fun `muting filters a whole chain by that author`() =
        runTest {
            val items =
                listOf(
                    FeedItemUi.SelfThreadChain(
                        persistentListOf(
                            samplePost("at://c1", authorDid = MUTED),
                            samplePost("at://c2", authorDid = MUTED),
                        ),
                    ),
                    single("at://keep", author = OTHER),
                )

            val kept = paged(items).withOptimisticState(mutes = setOf(MUTED)).asSnapshot()

            assertEquals(listOf("at://keep"), kept.map { it.leafId })
        }

    @Test
    fun `a tombstone is never filtered by a mute`() =
        runTest {
            // It carries no author to match on.
            val items = listOf(FeedItemUi.Blocked(uri = "at://blocked", authorDid = MUTED))

            val kept = paged(items).withOptimisticState(mutes = setOf(MUTED)).asSnapshot()

            assertEquals(1, kept.size)
        }

    @Test
    fun `unmuting flips the viewer flag without filtering anything`() =
        runTest {
            val items = listOf(single("at://mine", author = MUTED, mutedByViewer = true))

            val kept = paged(items).withOptimisticState(unmutes = setOf(MUTED)).asSnapshot()

            assertFalse((kept.single() as FeedItemUi.Single).post.viewer.isAuthorMutedByViewer)
        }

    @Test
    fun `unmuting reaches every matching post in a cluster and leaves the rest alone`() =
        runTest {
            val original = cluster(rootAuthor = MUTED, leafUri = "at://leaf", leafAuthor = OTHER)

            val kept = paged(listOf(original)).withOptimisticState(unmutes = setOf(MUTED)).asSnapshot()

            val item = kept.single() as FeedItemUi.ReplyCluster
            assertFalse(item.root.viewer.isAuthorMutedByViewer)
            assertSame(original.leaf, item.leaf)
        }

    @Test
    fun `a cached interaction state is overlaid onto the item`() =
        runTest {
            val kept =
                paged(listOf(single("at://a")))
                    .withOptimisticState(
                        interactions =
                            persistentMapOf(
                                "at://a" to PostInteractionState(viewerLikeUri = "at://like", likeCount = 42),
                            ),
                    ).asSnapshot()

            val item = kept.single() as FeedItemUi.Single
            assertEquals(42, item.post.stats.likeCount)
            assertEquals("at://like", item.post.viewer.likeUri)
        }

    @Test
    fun `the overlay reaches posts inside a cluster and a chain`() =
        runTest {
            val items =
                listOf(
                    cluster(rootAuthor = OTHER, leafUri = "at://leaf", leafAuthor = OTHER),
                    FeedItemUi.SelfThreadChain(persistentListOf(samplePost("at://c1"), samplePost("at://c2"))),
                )

            val kept =
                paged(items)
                    .withOptimisticState(
                        interactions =
                            persistentMapOf(
                                "at://leaf" to PostInteractionState(likeCount = 7),
                                "at://c1" to PostInteractionState(likeCount = 9),
                            ),
                    ).asSnapshot()

            assertEquals(7, (kept[0] as FeedItemUi.ReplyCluster).leaf.stats.likeCount)
            assertEquals(
                9,
                (kept[1] as FeedItemUi.SelfThreadChain)
                    .posts
                    .first()
                    .stats.likeCount,
            )
        }

    @Test
    fun `a deleted post leaves the list instead of being re-rendered`() =
        runTest {
            val kept =
                paged(listOf(single("at://gone"), single("at://stays")))
                    .withOptimisticState(
                        interactions = persistentMapOf("at://gone" to PostInteractionState(isDeleted = true)),
                    ).asSnapshot()

            assertEquals(listOf("at://stays"), kept.map { it.leafId })
        }

    @Test
    fun `deleting a chain's leaf removes the item, deleting its ancestor does not`() =
        runTest {
            // The check reads leafId, not key. It read `key` while the two were
            // the same value; now a chain is keyed on its FIRST post, so a
            // check on `key` would look "chain:…" up in a map of post URIs,
            // never match, and silently leave deleted posts in the feed.
            val chain =
                FeedItemUi.SelfThreadChain(
                    persistentListOf(samplePost("at://first"), samplePost("at://leaf")),
                )

            val ancestorDeleted =
                paged(listOf(chain))
                    .withOptimisticState(
                        interactions = persistentMapOf("at://first" to PostInteractionState(isDeleted = true)),
                    ).asSnapshot()
            assertEquals(1, ancestorDeleted.size)

            val leafDeleted =
                paged(listOf(chain))
                    .withOptimisticState(
                        interactions = persistentMapOf("at://leaf" to PostInteractionState(isDeleted = true)),
                    ).asSnapshot()
            assertEquals(emptyList<String>(), leafDeleted.map { it.leafId })
        }

    // ---------- what the VM does with the optimistic sets ----------

    @Test
    fun `muting calls the repository`() =
        feedTest {
            val mute = FakeMuteRepository()
            val vm = viewModel(muteRepository = mute)

            vm.onOverflowAction(samplePost("at://mine", authorDid = MUTED), PostOverflowAction.MuteAuthor)
            advanceUntilIdle()

            assertEquals(listOf(MUTED), mute.muteActorCalls)
        }

    @Test
    fun `a failed mute reports it, and the rollback is removing the DID`() =
        feedTest {
            // Rollback is removing the DID from the set, not restoring a saved
            // list: the rows were only filtered, so they return where they were.
            val mute = FakeMuteRepository().apply { nextMuteResult = Result.failure(IOException("down")) }
            val vm = viewModel(muteRepository = mute)

            vm.effects.test {
                vm.onOverflowAction(samplePost("at://mine", authorDid = MUTED), PostOverflowAction.MuteAuthor)
                advanceUntilIdle()
                assertEquals(FeedEffect.ShowError(FeedError.Network), awaitItem())
            }
        }

    @Test
    fun `unmuting calls the repository`() =
        feedTest {
            val mute = FakeMuteRepository()
            val vm = viewModel(muteRepository = mute)

            vm.onOverflowAction(samplePost("at://mine", authorDid = MUTED), PostOverflowAction.UnmuteAuthor)
            advanceUntilIdle()

            assertEquals(listOf(MUTED), mute.unmuteActorCalls)
        }

    @Test
    fun `a failed unmute reports it`() =
        feedTest {
            val mute = FakeMuteRepository().apply { nextUnmuteResult = Result.failure(IOException("down")) }
            val vm = viewModel(muteRepository = mute)

            vm.effects.test {
                vm.onOverflowAction(samplePost("at://mine", authorDid = MUTED), PostOverflowAction.UnmuteAuthor)
                advanceUntilIdle()
                assertEquals(FeedEffect.ShowError(FeedError.Network), awaitItem())
            }
        }

    // ---------- outgoing posts ----------

    @Test
    fun `the Following feed carries the user's just-sent posts`() =
        feedTest {
            val vm = viewModel()
            outgoingStore.set(outgoing("local-1", "at://sent"))
            advanceUntilIdle()

            assertEquals(
                listOf("local-1"),
                vm.uiState.value.outgoingPosts
                    .map { it.localId },
            )
        }

    @Test
    fun `other feeds carry none`() =
        feedTest {
            val vm = viewModel()
            outgoingStore.set(outgoing("local-1", "at://sent"))
            vm.handleEvent(FeedEvent.Bind(PinnedFeedsRepository.DISCOVER_FEED_URI, FeedKind.Generator))
            advanceUntilIdle()

            assertEquals(
                emptyList<String>(),
                vm.uiState.value.outgoingPosts
                    .map { it.localId },
            )
        }

    @Test
    fun `a reply is not shown as an outgoing row`() =
        feedTest {
            // Only top-level posts belong at the top of the timeline; a reply
            // belongs in its thread.
            val vm = viewModel()
            outgoingStore.set(outgoing("local-1", "at://sent", replyTo = "at://parent"))
            advanceUntilIdle()

            assertEquals(
                emptyList<String>(),
                vm.uiState.value.outgoingPosts
                    .map { it.localId },
            )
        }

    @Test
    fun `retry and discard go to the outgoing store`() =
        feedTest {
            val vm = viewModel()

            vm.handleEvent(FeedEvent.RetryOutgoing("local-1"))
            vm.handleEvent(FeedEvent.DiscardOutgoing("local-2"))

            assertEquals(listOf("local-1"), outgoingStore.retried)
            assertEquals(listOf("local-2"), outgoingStore.discarded)
        }

    // ---------- loaded-pages signal ----------

    @Test
    fun `loaded items are seeded into the interactions cache`() =
        feedTest {
            // Seeding is how wire data NEWER than a cached overlay replaces it.
            // Without it an overlay pins a stale count for the process's life.
            val cache = FakePostInteractionsCache()
            val vm = viewModel(cache = cache)

            vm.handleEvent(FeedEvent.ItemsLoaded(listOf(samplePost("at://a"), samplePost("at://b"))))

            assertEquals(listOf(listOf("at://a", "at://b")), cache.lastSeedPosts.map { page -> page.map { it.id } })
        }

    @Test
    fun `an empty load seeds nothing`() =
        feedTest {
            val cache = FakePostInteractionsCache()
            val vm = viewModel(cache = cache)

            vm.handleEvent(FeedEvent.ItemsLoaded(emptyList()))

            assertEquals(0, cache.seedCalls.get())
        }

    @Test
    fun `view_feed is logged once per feed, not per page`() =
        feedTest {
            val vm = viewModel()

            vm.handleEvent(FeedEvent.ItemsLoaded(listOf(samplePost("at://a"))))
            vm.handleEvent(FeedEvent.ItemsLoaded(listOf(samplePost("at://b"))))

            assertEquals(listOf(ViewFeed(FeedType.Following)), analytics.events)
        }

    @Test
    fun `view_feed reports the feed kind it was logged for`() =
        feedTest {
            val vm = viewModel()

            vm.handleEvent(FeedEvent.Bind(PinnedFeedsRepository.DISCOVER_FEED_URI, FeedKind.Generator))
            vm.handleEvent(FeedEvent.ItemsLoaded(listOf(samplePost("at://a"))))
            vm.handleEvent(FeedEvent.Bind("at://did:plc:gen/app.bsky.feed.generator/mine", FeedKind.Generator))
            vm.handleEvent(FeedEvent.ItemsLoaded(listOf(samplePost("at://b"))))
            vm.handleEvent(FeedEvent.Bind("at://did:plc:me/app.bsky.graph.list/friends", FeedKind.List))
            vm.handleEvent(FeedEvent.ItemsLoaded(listOf(samplePost("at://c"))))

            assertEquals(
                listOf(ViewFeed(FeedType.Discover), ViewFeed(FeedType.Custom), ViewFeed(FeedType.List)),
                analytics.events,
            )
        }

    // ---------- the reader's place ----------

    @Test
    fun `the pager opens at the saved place`() =
        feedTest {
            // Seeding the Pager is what makes the first page fetched the one
            // the reader was in, rather than the top of the feed.
            val repo = FakeFeedRepository()
            repo.storedAnchor = FeedAnchor(position = 37, itemKey = "single:at://post/a", offset = -24)
            val vm = viewModel(repo)

            vm.feed.first()

            assertEquals(listOf(37), repo.initialPositions)
        }

    @Test
    fun `a partition with no saved place opens at the top`() =
        feedTest {
            val repo = FakeFeedRepository()
            val vm = viewModel(repo)

            vm.feed.first()

            assertEquals(listOf<Int?>(null), repo.initialPositions)
        }

    @Test
    fun `the resolved place is offered to the screen, once`() =
        feedTest {
            // The VM never touches LazyListState (D9): it publishes where to
            // go and the list does the scrolling, then says it is done.
            val repo = FakeFeedRepository()
            repo.storedAnchor = FeedAnchor(position = 37, itemKey = "single:at://post/a", offset = -24)
            val vm = viewModel(repo)
            vm.feed.first()

            assertEquals(repo.storedAnchor, vm.restoreTarget.value)

            vm.handleEvent(FeedEvent.RestoreConsumed)
            assertNull(vm.restoreTarget.value)
        }

    @Test
    fun `a settled scroll saves the key and the offset`() =
        feedTest {
            val repo = FakeFeedRepository()
            val vm = viewModel(repo)

            vm.handleEvent(FeedEvent.AnchorChanged("single:at://post/a", offset = -24))
            advanceUntilIdle()

            assertEquals(listOf("single:at://post/a" to -24), repo.savedAnchors)
        }

    @Test
    fun `nothing is saved while signed out`() =
        feedTest {
            // There is no partition to key the place against.
            session.state.value = SessionState.SignedOut
            val repo = FakeFeedRepository()
            val vm = viewModel(repo)

            vm.handleEvent(FeedEvent.AnchorChanged("single:at://post/a", offset = 0))
            advanceUntilIdle()

            assertEquals(emptyList<Pair<String, Int>>(), repo.savedAnchors)
        }

    @Test
    fun `a refresh drops the saved place`() =
        feedTest {
            // The reader asked for a clean timeline (D8), so restoring them
            // into the old one would be the opposite of the gesture.
            val repo = FakeFeedRepository()
            repo.storedAnchor = FeedAnchor(position = 37, itemKey = "single:at://post/a", offset = -24)
            val vm = viewModel(repo)
            vm.feed.first()
            assertEquals(repo.storedAnchor, vm.restoreTarget.value)

            vm.handleEvent(FeedEvent.Refresh)
            advanceUntilIdle()

            assertNull(vm.restoreTarget.value)
            assertEquals(1, repo.anchorCleared)
        }

    // ---------- autoplay ----------

    @Test
    fun `state carries the resolved autoplay answer`() =
        feedTest {
            val vm = viewModel(autoplayEnabled = false)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.videoAutoplayEnabled)
        }

    @Test
    fun `autoplay defaults to enabled so the pre-emission frame does not flash a stopped feed`() =
        feedTest {
            // The policy's flow is cold, and seeding `false` would show every
            // video card with a play badge for a frame on every cold start.
            assertTrue(FeedState().videoAutoplayEnabled)
        }

    // ---------- navigation effects ----------

    @Test
    fun `tapping a post, author, image, quote, video or tag navigates`() =
        feedTest {
            val vm = viewModel()

            vm.effects.test {
                vm.handleEvent(FeedEvent.OnPostTapped(samplePost("at://p")))
                assertEquals(FeedEffect.NavigateToPost("at://p"), awaitItem())

                vm.handleEvent(FeedEvent.OnAuthorTapped("did:plc:someone"))
                assertEquals(FeedEffect.NavigateToAuthor("did:plc:someone"), awaitItem())

                vm.handleEvent(FeedEvent.OnImageTapped(samplePost("at://p"), 2))
                assertEquals(FeedEffect.NavigateToMediaViewer("at://p", 2), awaitItem())

                vm.handleEvent(FeedEvent.OnQuotedImageTapped("at://quoted", 1))
                assertEquals(FeedEffect.NavigateToMediaViewer("at://quoted", 1), awaitItem())

                vm.handleEvent(FeedEvent.OnQuotedPostTapped("at://quoted"))
                assertEquals(FeedEffect.NavigateToPost("at://quoted"), awaitItem())

                vm.handleEvent(FeedEvent.OnVideoTapped("at://video"))
                assertEquals(FeedEffect.NavigateToVideoPlayer("at://video"), awaitItem())

                vm.handleEvent(FeedEvent.OnTagTapped("kotlin", "alice.bsky.social"))
                assertEquals(FeedEffect.NavigateTo(Hashtag("kotlin", "alice.bsky.social")), awaitItem())
            }
        }

    // ---------- error mapping ----------

    @Test
    fun `load failures map to the categories the error screen renders`() {
        assertEquals(FeedError.Unauthenticated, NoSessionException().toFeedError())
        assertEquals(FeedError.Network, IOException("down").toFeedError())
        assertEquals(FeedError.FeedNotFound, xrpcError("UnknownFeed").toFeedError())
        assertEquals(
            FeedError.FeedOffline(serverMessage = "generator is down"),
            xrpcError("UpstreamFailure", "generator is down").toFeedError(),
        )
        assertEquals(
            FeedError.FeedOffline(serverMessage = "generator is down"),
            xrpcError("UpstreamTimeout", "generator is down").toFeedError(),
        )
    }

    @Test
    fun `a blank upstream message normalises to no message at all`() {
        // Otherwise the screen renders a dangling "Message from server:" label.
        assertEquals(FeedError.FeedOffline(serverMessage = null), xrpcError("UpstreamFailure", "").toFeedError())
    }

    @Test
    fun `an unmapped XrpcError is Unknown, not Network`() {
        // XrpcError is a RuntimeException, so the ordering in the mapping is
        // what keeps it from falling through to the IOException branch.
        assertTrue(xrpcError("SomethingElse").toFeedError() is FeedError.Unknown)
    }

    private companion object {
        const val VIEWER = "did:plc:viewer"
        const val MUTED = "did:plc:muted"
        const val OTHER = "did:plc:other"
    }
}

// ---------- fixtures ----------

/** One page of [items], the shape the transforms see. */
private fun paged(items: List<FeedItemUi>): Flow<PagingData<FeedItemUi>> = flowOf(PagingData.from(items))

/** The transform chain under test, with every input defaulting to "nothing optimistic". */
private fun Flow<PagingData<FeedItemUi>>.withOptimisticState(
    mutes: Set<String> = emptySet(),
    unmutes: Set<String> = emptySet(),
    interactions: PersistentMap<String, PostInteractionState> = persistentMapOf(),
): Flow<PagingData<FeedItemUi>> =
    withOptimisticState(
        mutedAuthors = flowOf(mutes),
        unmutedAuthors = flowOf(unmutes),
        interactions = flowOf(interactions),
    )

private fun single(
    uri: String,
    author: String = "did:plc:fake",
    mutedByViewer: Boolean = false,
): FeedItemUi.Single =
    FeedItemUi.Single(
        samplePost(uri, authorDid = author, viewer = ViewerStateUi(isAuthorMutedByViewer = mutedByViewer)),
    )

private fun cluster(
    rootAuthor: String,
    leafUri: String,
    leafAuthor: String,
): FeedItemUi.ReplyCluster =
    FeedItemUi.ReplyCluster(
        root = samplePost("at://root", authorDid = rootAuthor),
        parent = samplePost("at://parent", authorDid = rootAuthor),
        leaf = samplePost(leafUri, authorDid = leafAuthor),
        hasEllipsis = false,
    )

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

private fun outgoing(
    localId: String,
    uri: String,
    replyTo: String? = null,
): OutgoingPost =
    OutgoingPost(
        localId = localId,
        draft =
            PostDraft(
                text = "sent",
                attachments = emptyList(),
                replyTo = null,
                replyToUri = replyTo,
            ),
        post = samplePost(uri),
        status = OutgoingPostStatus.Sending,
    )

private fun xrpcError(
    name: String,
    message: String? = null,
): XrpcError =
    mockk<XrpcError>(relaxed = true) {
        every { errorName } returns name
        every { errorMessage } returns message
    }

private fun autoplayPolicy(videoAutoplayEnabled: Boolean = true): AutoplayPolicy =
    AutoplayPolicy(
        preferences =
            mockk(relaxed = true) {
                every { autoplayPreference } returns
                    flowOf(
                        if (videoAutoplayEnabled) AutoplayPreference.ALWAYS else AutoplayPreference.NEVER,
                    )
                every { autoplayGifs } returns flowOf(true)
            },
        networkStatus = mockk { every { isMetered } returns flowOf(false) },
    )

/** A signed-in viewer, which is what having a feed at all requires. */
private class FakeSessionStateProvider : SessionStateProvider {
    override val state =
        MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "viewer.bsky.social", did = "did:plc:viewer"))

    override suspend fun refresh() = Unit
}

/**
 * Serves one page of [items] for every partition, recording the keys it was
 * asked for so the binding tests can prove which partition was read.
 */
private class FakeFeedRepository(
    private val items: List<FeedItemUi> = emptyList(),
) : FeedRepository {
    val keys = mutableListOf<FeedKey>()
    val initialPositions = mutableListOf<Int?>()

    override fun pagedFeed(
        feedKey: FeedKey,
        initialPosition: Int?,
    ): Flow<PagingData<FeedItemUi>> {
        keys += feedKey
        initialPositions += initialPosition
        return flowOf(PagingData.from(items))
    }

    override fun head(
        feedKey: FeedKey,
        n: Int,
    ): Flow<List<PostUi>> = flowOf(emptyList())

    override suspend fun refresh(feedKey: FeedKey): Result<Boolean> = Result.success(true)

    override suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome> = Result.success(CatchUpOutcome.CAUGHT_UP)

    /** The saved place, as the screen would have written it. */
    var storedAnchor: FeedAnchor? = null
    val savedAnchors = mutableListOf<Pair<String, Int>>()
    var anchorCleared = 0

    override suspend fun anchor(feedKey: FeedKey): FeedAnchor? = storedAnchor

    override suspend fun saveAnchor(
        feedKey: FeedKey,
        itemKey: String,
        offset: Int,
    ) {
        savedAnchors += itemKey to offset
    }

    override suspend fun clearAnchor(feedKey: FeedKey) {
        anchorCleared++
    }

    override suspend fun trimToCap(
        feedKey: FeedKey,
        cap: Int,
        anchorItemPosition: Int?,
        ceiling: Int,
    ) = Unit

    override suspend fun clearAccount(accountDid: String) = Unit
}
