package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.feature.search.impl.data.FakeSearchActorsRepository
import net.kikin.nubecita.feature.search.impl.data.actorFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
            val vm = SearchActorsViewModel(repo)
            runCurrent()

            assertEquals(SearchActorsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun setQuery_nonBlank_fetchesFirstPage_emitsLoaded() =
        runTest {
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
                    net.kikin.nubecita.feature.search.impl.data.SearchActorsPage(
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
                    net.kikin.nubecita.feature.search.impl.data.SearchActorsPage(
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
            val vm = SearchActorsViewModel(repo)
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
                    net.kikin.nubecita.feature.search.impl.data.SearchActorsPage(
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
}
