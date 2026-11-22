package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.analytics.SearchPerform
import net.kikin.nubecita.core.analytics.SearchScope
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.feature.search.impl.data.FakeSearchStarterPacksRepository
import net.kikin.nubecita.feature.search.impl.data.SearchStarterPacksPage
import net.kikin.nubecita.feature.search.impl.data.packFixture
import net.kikin.nubecita.feature.search.impl.data.testSearchFirstPages
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchPacksViewModel], mirroring [SearchFeedsViewModelTest] case for case
 * (the pin cases have no counterpart: a pack has no pin).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPacksViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeSearchStarterPacksRepository()
    private val analytics = RecordingAnalyticsClient()
    private val firstPages = testSearchFirstPages(CoroutineScope(SupervisorJob() + testDispatcher), packs = repo)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = SearchPacksViewModel(repo, firstPages, analytics)

    @Test
    fun setQuery_blank_stateStaysIdle() =
        runTest {
            val vm = newVm()
            runCurrent()

            assertEquals(SearchPacksLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun setQuery_nonBlank_fetchesFirstPage_emitsLoaded() =
        runTest {
            val vm = newVm()
            val hit = packFixture(slug = "android", name = "Android")
            repo.respond(query = "art", cursor = null, items = listOf(hit), nextCursor = "c2")

            vm.setQuery("art")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPacksLoadStatus.Loaded, "expected Loaded, was $status")
            status as SearchPacksLoadStatus.Loaded
            assertEquals(listOf(hit), status.items.toList())
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
            assertEquals("art", vm.uiState.value.currentQuery)
        }

    @Test
    fun setQuery_nonBlank_emitsSearchPerformPacks() =
        runTest {
            val vm = newVm()
            repo.respond(query = "art", cursor = null, items = listOf(packFixture()), nextCursor = null)

            vm.setQuery("art", fromRecent = true)
            runCurrent()

            assertEquals(listOf(SearchPerform(scope = SearchScope.Packs, fromRecent = true)), analytics.events)
        }

    @Test
    fun setQuery_emptyResponse_emitsEmpty() =
        runTest {
            val vm = newVm()
            repo.respond(query = "no-matches", cursor = null, items = emptyList(), nextCursor = null)

            vm.setQuery("no-matches")
            runCurrent()

            assertEquals(SearchPacksLoadStatus.Empty, vm.uiState.value.loadStatus)
        }

    @Test
    fun setQuery_failure_emitsInitialError_withMappedError() =
        runTest {
            val vm = newVm()
            repo.fail(query = "art", cursor = null, throwable = java.io.IOException("disconnected"))

            vm.setQuery("art")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPacksLoadStatus.InitialError, "expected InitialError, was $status")
            assertEquals(SearchPacksError.Network, (status as SearchPacksLoadStatus.InitialError).error)
        }

    @Test
    fun loadMore_loaded_appendsNextPage_andClearsIsAppending() =
        runTest {
            val vm = newVm()
            val page1 = listOf(packFixture(slug = "a"))
            val page2 = listOf(packFixture(slug = "b"), packFixture(slug = "c"))
            repo.respond(query = "x", cursor = null, items = page1, nextCursor = "c2")
            repo.respond(query = "x", cursor = "c2", items = page2, nextCursor = "c3")

            vm.setQuery("x")
            runCurrent()
            vm.handleEvent(SearchPacksEvent.LoadMore)
            runCurrent()

            val status = vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded
            assertEquals((page1 + page2).map { it.uri }, status.items.map { it.uri })
            assertEquals("c3", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_endReached_isNoOp() =
        runTest {
            val vm = newVm()
            repo.respond(query = "x", cursor = null, items = listOf(packFixture()), nextCursor = null)

            vm.setQuery("x")
            runCurrent()
            val callsBefore = repo.callLog.size
            vm.handleEvent(SearchPacksEvent.LoadMore)
            runCurrent()

            assertEquals(callsBefore, repo.callLog.size)
            assertTrue((vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded).endReached)
        }

    @Test
    fun loadMore_alreadyAppending_isNoOp_singleFlight() =
        runTest {
            val vm = newVm()
            repo.respond(query = "x", cursor = null, items = listOf(packFixture()), nextCursor = "c2")
            repo.gate(query = "x", cursor = "c2")

            vm.setQuery("x")
            runCurrent()
            vm.handleEvent(SearchPacksEvent.LoadMore)
            runCurrent()
            val callsAfterFirstLoadMore = repo.callLog.size
            val statusMid = vm.uiState.value.loadStatus
            assertTrue(statusMid is SearchPacksLoadStatus.Loaded)
            assertEquals(true, (statusMid as SearchPacksLoadStatus.Loaded).isAppending)

            vm.handleEvent(SearchPacksEvent.LoadMore)
            runCurrent()

            assertEquals(callsAfterFirstLoadMore, repo.callLog.size, "concurrent LoadMore must not double-fire the repo")
        }

    @Test
    fun setQuery_rapidChange_cancelsPrior_viaMapLatest() =
        runTest {
            val vm = newVm()
            val staleGate = repo.gate(query = "ar", cursor = null)
            repo.respond(query = "art", cursor = null, items = listOf(packFixture(slug = "art")), nextCursor = null)

            vm.setQuery("ar")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPacksLoadStatus.InitialLoading)

            vm.setQuery("art")
            runCurrent()
            staleGate.complete(Result.success(SearchStarterPacksPage(persistentListOf(packFixture(slug = "stale")), null)))
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPacksLoadStatus.Loaded, "expected Loaded for art, was $status")
            assertEquals(packFixture(slug = "art").uri, (status as SearchPacksLoadStatus.Loaded).items.single().uri, "stale 'ar' completion must not clobber 'art' results")
        }

    @Test
    fun loadMore_inFlight_whenQueryChanges_doesNotClobberNewQueryItems() =
        runTest {
            val vm = newVm()
            repo.respond(query = "art", cursor = null, items = listOf(packFixture(slug = "art")), nextCursor = "c2")
            repo.respond(query = "books", cursor = null, items = listOf(packFixture(slug = "books")), nextCursor = null)
            val pageTwoArtGate = repo.gate(query = "art", cursor = "c2")

            vm.setQuery("art")
            runCurrent()
            vm.handleEvent(SearchPacksEvent.LoadMore)
            runCurrent()
            assertTrue((vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded).isAppending, "page-2-art fetch should be in flight")

            vm.setQuery("books")
            runCurrent()
            assertEquals(listOf(packFixture(slug = "books").uri), (vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded).items.map { it.uri })

            pageTwoArtGate.complete(Result.success(SearchStarterPacksPage(persistentListOf(packFixture(slug = "stale")), "c3")))
            runCurrent()

            val finalStatus = vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded
            assertEquals(listOf(packFixture(slug = "books").uri), finalStatus.items.map { it.uri }, "stale art-page-2 items must not appear on the books list")
            assertEquals(null, finalStatus.nextCursor, "cursor must remain books' null cursor")
        }

    @Test
    fun loadMore_failure_emitsShowAppendError_keepsExistingItems() =
        runTest {
            val vm = newVm()
            repo.respond(query = "x", cursor = null, items = listOf(packFixture()), nextCursor = "c2")
            repo.fail(query = "x", cursor = "c2", throwable = java.io.IOException("flap"))

            vm.setQuery("x")
            runCurrent()

            vm.effects.test {
                vm.handleEvent(SearchPacksEvent.LoadMore)
                runCurrent()
                val effect = awaitItem()
                assertTrue(effect is SearchPacksEffect.ShowAppendError)
                assertEquals(SearchPacksError.Network, (effect as SearchPacksEffect.ShowAppendError).error)
                cancelAndIgnoreRemainingEvents()
            }

            val status = vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded
            assertEquals(false, status.isAppending)
            assertEquals(1, status.items.size)
        }

    @Test
    fun retry_initialError_retriggersFirstPage_viaIncarnationBump() =
        runTest {
            val vm = newVm()
            repo.fail(query = "x", cursor = null, throwable = java.io.IOException("network"))

            vm.setQuery("x")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPacksLoadStatus.InitialError)

            repo.clearGate(query = "x", cursor = null)
            repo.respond(query = "x", cursor = null, items = listOf(packFixture()), nextCursor = null)
            vm.handleEvent(SearchPacksEvent.Retry)
            runCurrent()

            assertTrue(vm.uiState.value.loadStatus is SearchPacksLoadStatus.Loaded)
        }

    @Test
    fun clearQueryClicked_emitsNavigateToClearQueryEffect() =
        runTest {
            val vm = newVm()
            vm.effects.test {
                vm.handleEvent(SearchPacksEvent.ClearQueryClicked)
                assertEquals(SearchPacksEffect.NavigateToClearQuery, awaitItem())
            }
        }

    @Test
    fun packTapped_emitsNavigateToPackEffect() =
        runTest {
            val vm = newVm()
            vm.effects.test {
                vm.handleEvent(SearchPacksEvent.PackTapped("at://did:plc:c/app.bsky.graph.starterpack/p"))
                assertEquals(SearchPacksEffect.NavigateToPack("at://did:plc:c/app.bsky.graph.starterpack/p"), awaitItem())
            }
        }

    @Test
    fun setQuery_becomesBlankAfterLoaded_resetsToIdle() =
        runTest {
            val vm = newVm()
            repo.respond(query = "art", cursor = null, items = listOf(packFixture()), nextCursor = null)

            vm.setQuery("art")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPacksLoadStatus.Loaded)

            vm.setQuery("")
            runCurrent()

            assertEquals(SearchPacksLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
        }

    @Test
    fun `resubmitting the same query refetches`() =
        runTest {
            val vm = newVm()
            vm.setQuery("kotlin", submission = 1)
            runCurrent()
            assertEquals(1, repo.callLog.size)

            vm.setQuery("kotlin", submission = 1)
            runCurrent()
            assertEquals(1, repo.callLog.size, "the same submission is the same search")

            vm.setQuery("kotlin", submission = 2)
            runCurrent()

            assertEquals(2, repo.callLog.size)
        }

    @Test
    fun `a pack repeated in the first page shows once`() =
        runTest {
            val vm = newVm()
            val a = packFixture(slug = "a")
            repo.respond(query = "x", cursor = null, items = listOf(a, packFixture(slug = "b"), a), nextCursor = null)

            vm.setQuery("x", submission = 1)
            runCurrent()

            val status = vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded
            assertEquals(listOf(a.uri, packFixture(slug = "b").uri), status.items.map { it.uri })
        }

    @Test
    fun `a pack repeated inside the appended page shows once`() =
        runTest {
            val vm = newVm()
            val c = packFixture(slug = "c")
            repo.respond(query = "x", cursor = null, items = listOf(packFixture(slug = "a")), nextCursor = "c2")
            repo.respond(query = "x", cursor = "c2", items = listOf(packFixture(slug = "a"), c, c), nextCursor = null)

            vm.setQuery("x", submission = 1)
            runCurrent()
            vm.handleEvent(SearchPacksEvent.LoadMore)
            runCurrent()

            val status = vm.uiState.value.loadStatus as SearchPacksLoadStatus.Loaded
            assertEquals(listOf(packFixture(slug = "a").uri, c.uri), status.items.map { it.uri })
        }
}
