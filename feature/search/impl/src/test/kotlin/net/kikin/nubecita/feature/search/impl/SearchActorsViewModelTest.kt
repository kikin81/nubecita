package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.SearchPerform
import net.kikin.nubecita.core.analytics.SearchScope
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.search.impl.data.FakeSearchActorsRepository
import net.kikin.nubecita.feature.search.impl.data.FollowOverlay
import net.kikin.nubecita.feature.search.impl.data.FollowOverride
import net.kikin.nubecita.feature.search.impl.data.FollowToggle
import net.kikin.nubecita.feature.search.impl.data.actorFixture
import net.kikin.nubecita.feature.search.impl.data.seed
import net.kikin.nubecita.feature.search.impl.data.signedIn
import net.kikin.nubecita.feature.search.impl.data.testSearchFirstPages
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchActorsViewModel]. Same harness shape as
 * SearchPostsViewModelTest — `Dispatchers.setMain(UnconfinedTestDispatcher())`
 * + `runTest { runCurrent() }`. No snapshotFlow involved, no debounce
 * inside this VM, so no `Snapshot.sendApplyNotifications()` /
 * `advanceTimeBy` needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchActorsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeSearchActorsRepository()
    private val analytics = RecordingAnalyticsClient()
    private val followRepo = FakeFollowRepository()
    private val session = MutableStateFlow<SessionState>(signedIn("did:plc:me"))
    private val sessionProvider =
        object : SessionStateProvider {
            override val state = session

            override suspend fun refresh() = Unit
        }
    private val overlay = FollowOverlay(CoroutineScope(SupervisorJob() + testDispatcher), sessionProvider)

    private fun newVm(follow: FollowRepository = followRepo) = SearchActorsViewModel(repo, firstPages, FollowToggle(follow, overlay, sessionProvider, CoroutineScope(SupervisorJob() + testDispatcher)), overlay, sessionProvider, analytics)

    private val firstPages = testSearchFirstPages(CoroutineScope(SupervisorJob() + testDispatcher), actors = repo)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setQuery_blank_stateStaysIdle() =
        runTest {
            val vm = newVm()
            runCurrent()

            assertEquals(SearchActorsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun search_logsSearchPerform_peopleScope_withFromRecent() =
        runTest {
            val vm = newVm()
            repo.respond(query = "alice", cursor = null, items = emptyList(), nextCursor = null)

            vm.setQuery("alice", fromRecent = true)
            runCurrent()

            assertEquals(
                listOf(SearchPerform(scope = SearchScope.People, fromRecent = true)),
                analytics.events,
            )
        }

    @Test
    fun setQuery_nonBlank_fetchesFirstPage_emitsLoaded() =
        runTest {
            val vm = newVm()
            val hit = actorFixture(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice")
            repo.respond(
                query = "alice",
                cursor = null,
                items = listOf(hit),
                nextCursor = "c2",
            )

            vm.setQuery("alice")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded, "expected Loaded, was $status")
            status as SearchActorsLoadStatus.Loaded
            assertEquals(listOf(hit), status.items.toList())
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
            assertEquals("alice", vm.uiState.value.currentQuery)
        }

    @Test
    fun setQuery_emptyResponse_emitsEmpty() =
        runTest {
            val vm = newVm()
            repo.respond(
                query = "no-matches",
                cursor = null,
                items = emptyList(),
                nextCursor = null,
            )

            vm.setQuery("no-matches")
            runCurrent()

            assertEquals(SearchActorsLoadStatus.Empty, vm.uiState.value.loadStatus)
        }

    @Test
    fun setQuery_failure_emitsInitialError_withMappedError() =
        runTest {
            val vm = newVm()
            repo.fail(query = "alice", cursor = null, throwable = java.io.IOException("disconnected"))

            vm.setQuery("alice")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.InitialError, "expected InitialError, was $status")
            assertEquals(
                SearchActorsError.Network,
                (status as SearchActorsLoadStatus.InitialError).error,
            )
        }

    @Test
    fun setQuery_rapidChange_cancelsPrior_viaMapLatest() =
        runTest {
            val vm = newVm()
            val aliGate = repo.gate(query = "ali", cursor = null)
            repo.respond(
                query = "alic",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice")),
                nextCursor = null,
            )

            vm.setQuery("ali")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.InitialLoading)

            vm.setQuery("alic")
            runCurrent()

            aliGate.complete(
                Result.success(
                    ActorSearchPage(
                        items =
                            kotlinx.collections.immutable.persistentListOf(
                                actorFixture(did = "did:plc:stale", handle = "stale.bsky.social", displayName = "Stale"),
                            ),
                        nextCursor = null,
                    ),
                ),
            )
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded, "expected Loaded for alic, was $status")
            assertEquals(
                "did:plc:alice",
                (status as SearchActorsLoadStatus.Loaded).items.single().did,
                "stale 'ali' completion must not clobber 'alic' results",
            )
        }

    @Test
    fun loadMore_loaded_appendsNextPage_andClearsIsAppending() =
        runTest {
            val vm = newVm()
            val page1 = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social"))
            val page2 =
                listOf(
                    actorFixture(did = "did:plc:b", handle = "b.bsky.social"),
                    actorFixture(did = "did:plc:c", handle = "c.bsky.social"),
                )
            repo.respond(query = "x", cursor = null, items = page1, nextCursor = "c2")
            repo.respond(query = "x", cursor = "c2", items = page2, nextCursor = "c3")

            vm.setQuery("x")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.Loaded)

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded)
            status as SearchActorsLoadStatus.Loaded
            assertEquals(
                listOf("did:plc:a", "did:plc:b", "did:plc:c"),
                status.items.map { it.did },
            )
            assertEquals("c3", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_endReached_isNoOp() =
        runTest {
            val vm = newVm()
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social")),
                nextCursor = null,
            )

            vm.setQuery("x")
            runCurrent()
            val beforeCallCount = repo.callLog.size

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()

            assertEquals(beforeCallCount, repo.callLog.size, "endReached must short-circuit before hitting repo")
        }

    @Test
    fun loadMore_alreadyAppending_isNoOp_singleFlight() =
        runTest {
            val vm = newVm()
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social")),
                nextCursor = "c2",
            )
            // Gate the second-page fetch so isAppending stays true.
            repo.gate(query = "x", cursor = "c2")

            vm.setQuery("x")
            runCurrent()

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()
            val callsAfterFirstLoadMore = repo.callLog.size
            val statusMid = vm.uiState.value.loadStatus
            assertTrue(statusMid is SearchActorsLoadStatus.Loaded)
            assertEquals(true, (statusMid as SearchActorsLoadStatus.Loaded).isAppending)

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()
            assertEquals(
                callsAfterFirstLoadMore,
                repo.callLog.size,
                "concurrent LoadMore must not double-fire the repo",
            )
        }

    @Test
    fun loadMore_failure_emitsShowAppendError_keepsExistingItems() =
        runTest {
            val vm = newVm()
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social")),
                nextCursor = "c2",
            )
            repo.fail(query = "x", cursor = "c2", throwable = java.io.IOException("flap"))

            vm.setQuery("x")
            runCurrent()

            vm.effects.test {
                vm.handleEvent(SearchActorsEvent.LoadMore)
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchActorsEffect.ShowAppendError)
                assertEquals(SearchActorsError.Network, (effect as SearchActorsEffect.ShowAppendError).error)
                cancelAndIgnoreRemainingEvents()
            }

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded)
            status as SearchActorsLoadStatus.Loaded
            assertEquals(listOf("did:plc:a"), status.items.map { it.did })
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_inFlight_whenQueryChanges_doesNotClobberNewQueryItems() =
        runTest {
            // Regression test for the stale-completion guard inherited
            // from vrba.6's code-quality review.
            val vm = newVm()
            // Page 1 "alice": one item + nextCursor=c2 so loadMore is valid.
            repo.respond(
                query = "alice",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:alice", handle = "alice.bsky.social")),
                nextCursor = "c2",
            )
            // Page 1 "bob": a single fresh item, end-of-results.
            repo.respond(
                query = "bob",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:bob", handle = "bob.bsky.social")),
                nextCursor = null,
            )
            // Gate the page-2-alice fetch so we control completion timing.
            val pageTwoAliceGate = repo.gate(query = "alice", cursor = "c2")

            // 1. Initial query → Loaded(alice).
            vm.setQuery("alice")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.Loaded)

            // 2. LoadMore on alice → isAppending=true, page-2-alice fetch suspended.
            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()
            assertTrue(
                (vm.uiState.value.loadStatus as SearchActorsLoadStatus.Loaded).isAppending,
                "page-2-alice fetch should be in flight",
            )

            // 3. User types a different query. mapLatest fires runFirstPage(bob).
            vm.setQuery("bob")
            runCurrent()
            val afterTyping = vm.uiState.value.loadStatus
            assertTrue(afterTyping is SearchActorsLoadStatus.Loaded)
            assertEquals(
                "did:plc:bob",
                (afterTyping as SearchActorsLoadStatus.Loaded).items.single().did,
                "bob results should have landed",
            )

            // 4. The stale page-2-alice completion arrives AFTER the query change.
            //    Without the stale guard, this would splice alice-page-2 items
            //    onto the bob list.
            pageTwoAliceGate.complete(
                Result.success(
                    ActorSearchPage(
                        items =
                            kotlinx.collections.immutable.persistentListOf(
                                actorFixture(did = "did:plc:stale-alice", handle = "stale.bsky.social"),
                            ),
                        nextCursor = "c3",
                    ),
                ),
            )
            runCurrent()

            val finalStatus = vm.uiState.value.loadStatus
            assertTrue(finalStatus is SearchActorsLoadStatus.Loaded)
            finalStatus as SearchActorsLoadStatus.Loaded
            assertEquals(
                listOf("did:plc:bob"),
                finalStatus.items.map { it.did },
                "stale alice-page-2 items must not appear on the bob list",
            )
            assertEquals(null, finalStatus.nextCursor, "cursor must remain bob's null cursor")
        }

    @Test
    fun retry_initialError_retriggersFirstPage_viaIncarnationBump() =
        runTest {
            val vm = newVm()
            repo.fail(query = "x", cursor = null, throwable = java.io.IOException("network"))

            vm.setQuery("x")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.InitialError)

            // Replace the failure with success for the same key.
            repo.clearGate(query = "x", cursor = null)
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:retry", handle = "retry.bsky.social")),
                nextCursor = null,
            )

            vm.handleEvent(SearchActorsEvent.Retry)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded, "expected Loaded after retry, was $status")
        }

    @Test
    fun actorTapped_emitsNavigateToProfileEffect() =
        runTest {
            val vm = newVm()
            vm.effects.test {
                vm.handleEvent(SearchActorsEvent.ActorTapped("alice.bsky.social"))
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchActorsEffect.NavigateToProfile)
                assertEquals(
                    "alice.bsky.social",
                    (effect as SearchActorsEffect.NavigateToProfile).handle,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearQueryClicked_emitsNavigateToClearQueryEffect() =
        runTest {
            val vm = newVm()
            vm.effects.test {
                vm.handleEvent(SearchActorsEvent.ClearQueryClicked)
                runCurrent()

                assertEquals(SearchActorsEffect.NavigateToClearQuery, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setQuery_becomesBlankAfterLoaded_resetsToIdle() =
        runTest {
            // Regression for Copilot finding on PR #199: the prior
            // `.filter { isNotBlank() }` shape silently kept the stale
            // Loaded state visible after the user cleared the field.
            val vm = newVm()
            repo.respond(
                query = "alice",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:alice", handle = "alice.bsky.social")),
                nextCursor = null,
            )

            vm.setQuery("alice")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.Loaded)

            vm.setQuery("")
            runCurrent()

            assertEquals(SearchActorsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
        }

    @Test
    fun setQuery_becomesBlankWhileFetchInFlight_cancelsAndResetsToIdle() =
        runTest {
            // Regression: with `.filter { isNotBlank() }` upstream of
            // mapLatest, a blank emission would NOT cancel an in-flight
            // runFirstPage — so the prior fetch could resolve late and
            // overwrite the Idle reset with a stale Loaded.
            val vm = newVm()
            val aliceGate = repo.gate(query = "alice", cursor = null)

            vm.setQuery("alice")
            runCurrent()
            assertEquals(SearchActorsLoadStatus.InitialLoading, vm.uiState.value.loadStatus)

            // User clears the field while the fetch is still in flight.
            vm.setQuery("")
            runCurrent()
            assertEquals(SearchActorsLoadStatus.Idle, vm.uiState.value.loadStatus)

            // The cancelled alice fetch completes late. mapLatest should
            // have cancelled it before the onSuccess could fire; the
            // setState below must NOT land.
            aliceGate.complete(
                Result.success(
                    ActorSearchPage(
                        items =
                            kotlinx.collections.immutable.persistentListOf(
                                actorFixture(did = "did:plc:stale", handle = "stale.bsky.social"),
                            ),
                        nextCursor = null,
                    ),
                ),
            )
            runCurrent()

            assertEquals(
                SearchActorsLoadStatus.Idle,
                vm.uiState.value.loadStatus,
                "stale alice completion must not clobber Idle after the field was cleared",
            )
        }

    @Test
    fun `resubmitting the same query refetches`() =
        runTest {
            val vm = newVm()
            vm.setQuery("alice", submission = 1)
            runCurrent()
            assertEquals(1, repo.callLog.size)

            vm.setQuery("alice", submission = 1)
            runCurrent()
            assertEquals(1, repo.callLog.size, "the same submission is the same search")

            vm.setQuery("alice", submission = 2)
            runCurrent()

            assertEquals(2, repo.callLog.size)
        }

    // -------------------------------------------------------------------------
    // Follow toggle — same mechanism as DiscoverViewModel.onFollowTapped
    // -------------------------------------------------------------------------

    private fun loadedWith(vararg actors: ActorUi): SearchActorsViewModel {
        val vm = newVm()
        repo.respond(query = "sci", cursor = null, items = actors.toList(), nextCursor = null)
        vm.setQuery("sci")
        return vm
    }

    private fun SearchActorsViewModel.items(): List<ActorUi> = (uiState.value.loadStatus as SearchActorsLoadStatus.Loaded).items.toList()

    @Test
    fun `follow success flips isFollowing, stores the follow uri and logs Follow`() =
        runTest {
            followRepo.followResult = Result.success("at://did:plc:me/app.bsky.graph.follow/abc")
            val vm = loadedWith(actorFixture(did = "did:plc:sci", handle = "sci.bsky.social"))
            runCurrent()

            vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
            runCurrent()

            val actor = vm.items().single()
            assertTrue(actor.isFollowing)
            assertEquals("at://did:plc:me/app.bsky.graph.follow/abc", actor.followUri)
            assertEquals(listOf("did:plc:sci"), followRepo.followCallDids)
            assertEquals(listOf(InteractActor(ActorAction.Follow, PostSurface.Search)), analytics.events.filterIsInstance<InteractActor>())
        }

    @Test
    fun `follow is optimistic while the request is in flight, and a second tap is dropped`() =
        runTest {
            val gate = CompletableDeferred<Result<String>>()
            val gatedFollow =
                object : FollowRepository {
                    var calls = 0

                    override suspend fun follow(subjectDid: String): Result<String> {
                        calls++
                        return gate.await()
                    }

                    override suspend fun unfollow(followUri: String): Result<Unit> = Result.success(Unit)
                }
            val vm = newVm(follow = gatedFollow)
            repo.respond(query = "sci", cursor = null, items = listOf(actorFixture(did = "did:plc:sci")), nextCursor = null)
            vm.setQuery("sci")
            runCurrent()

            vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
            vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
            runCurrent()

            assertTrue(vm.items().single().isFollowing, "flipped before the server answered")
            assertEquals(1, gatedFollow.calls, "the concurrent tap is dropped")

            gate.complete(Result.success("at://follow/1"))
            runCurrent()
            assertEquals("at://follow/1", vm.items().single().followUri)
        }

    @Test
    fun `follow failure rolls back and emits FollowFailed`() =
        runTest {
            followRepo.followResult = Result.failure(java.io.IOException("net"))
            val vm = loadedWith(actorFixture(did = "did:plc:sci"))
            runCurrent()

            vm.effects.test {
                vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
                runCurrent()

                assertEquals(SearchActorsEffect.FollowFailed(wasUnfollow = false), awaitItem())
            }
            val actor = vm.items().single()
            assertFalse(actor.isFollowing, "rolled back to not following")
            assertNull(actor.followUri)
            assertTrue(analytics.events.filterIsInstance<InteractActor>().isEmpty(), "no analytics on failure")
        }

    @Test
    fun `unfollow success clears isFollowing and the follow uri and logs Unfollow`() =
        runTest {
            val vm =
                loadedWith(
                    actorFixture(did = "did:plc:sci").copy(isFollowing = true, followUri = "at://follow/9"),
                )
            runCurrent()

            vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
            runCurrent()

            val actor = vm.items().single()
            assertFalse(actor.isFollowing)
            assertNull(actor.followUri)
            assertEquals(listOf("at://follow/9"), followRepo.unfollowCallUris)
            assertEquals(listOf(InteractActor(ActorAction.Unfollow, PostSurface.Search)), analytics.events.filterIsInstance<InteractActor>())
        }

    @Test
    fun `unfollow failure restores the follow state and emits FollowFailed for an unfollow`() =
        runTest {
            followRepo.unfollowResult = Result.failure(java.io.IOException("net"))
            val vm =
                loadedWith(
                    actorFixture(did = "did:plc:sci").copy(isFollowing = true, followUri = "at://follow/9"),
                )
            runCurrent()

            vm.effects.test {
                vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
                runCurrent()

                assertEquals(SearchActorsEffect.FollowFailed(wasUnfollow = true), awaitItem())
            }
            val actor = vm.items().single()
            assertTrue(actor.isFollowing)
            assertEquals("at://follow/9", actor.followUri)
        }

    // -------------------------------------------------------------------------
    // Shared follow overlay
    // -------------------------------------------------------------------------

    @Test
    fun `a page served again after a follow shows the followed state`() =
        runTest {
            followRepo.followResult = Result.success("at://follow/new")
            val page = listOf(actorFixture(did = "did:plc:sci"))
            repo.respond(query = "sci", cursor = null, items = page, nextCursor = null)
            val first = newVm()
            first.setQuery("sci", submission = 1)
            runCurrent()
            first.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
            runCurrent()

            // A new reader of the same cached first page (same submission).
            val second = newVm()
            second.setQuery("sci", submission = 1)
            runCurrent()

            val actor = second.items().single()
            assertTrue(actor.isFollowing)
            assertEquals("at://follow/new", actor.followUri)
            assertEquals(1, repo.callLog.size, "served from the shared first-page cache")
        }

    @Test
    fun `a failed follow removes its override`() =
        runTest {
            followRepo.followResult = Result.failure(java.io.IOException("net"))
            val vm = loadedWith(actorFixture(did = "did:plc:sci"))
            runCurrent()

            vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:sci"))
            runCurrent()

            assertFalse(overlay.current().containsKey("did:plc:sci"))
        }

    @Test
    fun `an override written elsewhere updates a loaded list`() =
        runTest {
            val vm = loadedWith(actorFixture(did = "did:plc:sci"))
            runCurrent()
            val requests = repo.callLog.size

            overlay.seed("did:plc:sci", FollowOverride(isFollowing = true, followUri = "at://follow/x"))
            runCurrent()

            val actor = vm.items().single()
            assertTrue(actor.isFollowing)
            assertEquals("at://follow/x", actor.followUri)
            assertEquals(requests, repo.callLog.size, "no new request")
        }

    @Test
    fun `the signed-in account's row has no follow action`() =
        runTest {
            val vm = loadedWith(actorFixture(did = "did:plc:me"), actorFixture(did = "did:plc:sci"))
            runCurrent()

            assertEquals("did:plc:me", vm.uiState.value.viewerDid)
            vm.handleEvent(SearchActorsEvent.FollowTapped("did:plc:me"))
            runCurrent()

            assertTrue(followRepo.followCallDids.isEmpty(), "following yourself is never attempted")
            assertFalse(vm.items().first { it.did == "did:plc:me" }.isFollowing)
        }
}
