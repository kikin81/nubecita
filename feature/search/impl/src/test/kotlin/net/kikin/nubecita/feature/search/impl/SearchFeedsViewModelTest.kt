package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.feature.search.impl.data.FakeSearchFeedsRepository
import net.kikin.nubecita.feature.search.impl.data.feedFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchFeedsViewModel]. Same harness shape as
 * SearchActorsViewModelTest — `Dispatchers.setMain(UnconfinedTestDispatcher())`
 * + `runTest { runCurrent() }`. No snapshotFlow involved, no debounce
 * inside this VM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchFeedsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeSearchFeedsRepository()

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
            val vm = SearchFeedsViewModel(repo)
            runCurrent()

            assertEquals(SearchFeedsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun setQuery_nonBlank_fetchesFirstPage_emitsLoaded() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            val hit = feedFixture(uri = "at://did:plc:f1/app.bsky.feed.generator/discover", displayName = "Discover")
            repo.respond(query = "art", cursor = null, items = listOf(hit), nextCursor = "c2")

            vm.setQuery("art")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchFeedsLoadStatus.Loaded, "expected Loaded, was $status")
            status as SearchFeedsLoadStatus.Loaded
            assertEquals(listOf(hit), status.items.toList())
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
            assertEquals("art", vm.uiState.value.currentQuery)
        }

    @Test
    fun setQuery_emptyResponse_emitsEmpty() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            repo.respond(query = "no-matches", cursor = null, items = emptyList(), nextCursor = null)

            vm.setQuery("no-matches")
            runCurrent()

            assertEquals(SearchFeedsLoadStatus.Empty, vm.uiState.value.loadStatus)
        }

    @Test
    fun setQuery_failure_emitsInitialError_withMappedError() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            repo.fail(query = "art", cursor = null, throwable = java.io.IOException("disconnected"))

            vm.setQuery("art")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchFeedsLoadStatus.InitialError, "expected InitialError, was $status")
            assertEquals(
                SearchFeedsError.Network,
                (status as SearchFeedsLoadStatus.InitialError).error,
            )
        }

    @Test
    fun loadMore_loaded_appendsNextPage_andClearsIsAppending() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            val page1 = listOf(feedFixture(uri = "at://did:plc:a/app.bsky.feed.generator/a"))
            val page2 =
                listOf(
                    feedFixture(uri = "at://did:plc:b/app.bsky.feed.generator/b"),
                    feedFixture(uri = "at://did:plc:c/app.bsky.feed.generator/c"),
                )
            repo.respond(query = "x", cursor = null, items = page1, nextCursor = "c2")
            repo.respond(query = "x", cursor = "c2", items = page2, nextCursor = "c3")

            vm.setQuery("x")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchFeedsLoadStatus.Loaded)

            vm.handleEvent(SearchFeedsEvent.LoadMore)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchFeedsLoadStatus.Loaded)
            status as SearchFeedsLoadStatus.Loaded
            assertEquals(
                listOf(
                    "at://did:plc:a/app.bsky.feed.generator/a",
                    "at://did:plc:b/app.bsky.feed.generator/b",
                    "at://did:plc:c/app.bsky.feed.generator/c",
                ),
                status.items.map { it.uri },
            )
            assertEquals("c3", status.nextCursor)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_endReached_isNoOp() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            repo.respond(query = "x", cursor = null, items = listOf(feedFixture()), nextCursor = null)

            vm.setQuery("x")
            runCurrent()
            val beforeCallCount = repo.callLog.size

            vm.handleEvent(SearchFeedsEvent.LoadMore)
            runCurrent()

            assertEquals(beforeCallCount, repo.callLog.size, "endReached must short-circuit before hitting repo")
        }

    @Test
    fun loadMore_failure_emitsShowAppendError_keepsExistingItems() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            repo.respond(query = "x", cursor = null, items = listOf(feedFixture()), nextCursor = "c2")
            repo.fail(query = "x", cursor = "c2", throwable = java.io.IOException("flap"))

            vm.setQuery("x")
            runCurrent()

            vm.effects.test {
                vm.handleEvent(SearchFeedsEvent.LoadMore)
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchFeedsEffect.ShowAppendError)
                assertEquals(SearchFeedsError.Network, (effect as SearchFeedsEffect.ShowAppendError).error)
                cancelAndIgnoreRemainingEvents()
            }

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchFeedsLoadStatus.Loaded)
            assertEquals(false, (status as SearchFeedsLoadStatus.Loaded).isAppending)
        }

    @Test
    fun retry_initialError_retriggersFirstPage_viaIncarnationBump() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            repo.fail(query = "x", cursor = null, throwable = java.io.IOException("network"))

            vm.setQuery("x")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchFeedsLoadStatus.InitialError)

            repo.clearGate(query = "x", cursor = null)
            repo.respond(query = "x", cursor = null, items = listOf(feedFixture()), nextCursor = null)

            vm.handleEvent(SearchFeedsEvent.Retry)
            runCurrent()

            assertTrue(vm.uiState.value.loadStatus is SearchFeedsLoadStatus.Loaded)
        }

    @Test
    fun clearQueryClicked_emitsNavigateToClearQueryEffect() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            vm.effects.test {
                vm.handleEvent(SearchFeedsEvent.ClearQueryClicked)
                runCurrent()

                assertEquals(SearchFeedsEffect.NavigateToClearQuery, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun feedTapped_isNoOp_emitsNoEffect() =
        runTest {
            // V1: no feed-detail feature exists, so FeedTapped is a no-op
            // in the VM. When :feature:feeddetail:api lands, replace this
            // assertion with a NavigateToFeed effect expectation.
            val vm = SearchFeedsViewModel(repo)
            vm.effects.test {
                vm.handleEvent(SearchFeedsEvent.FeedTapped(uri = "at://did:plc:f/app.bsky.feed.generator/x"))
                runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setQuery_becomesBlankAfterLoaded_resetsToIdle() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            repo.respond(query = "art", cursor = null, items = listOf(feedFixture()), nextCursor = null)

            vm.setQuery("art")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchFeedsLoadStatus.Loaded)

            vm.setQuery("")
            runCurrent()

            assertEquals(SearchFeedsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
        }
}
