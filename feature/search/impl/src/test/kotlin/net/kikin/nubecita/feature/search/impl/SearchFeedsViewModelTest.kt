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
    fun loadMore_alreadyAppending_isNoOp_singleFlight() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            repo.respond(query = "x", cursor = null, items = listOf(feedFixture()), nextCursor = "c2")
            // Gate the second-page fetch so isAppending stays true.
            repo.gate(query = "x", cursor = "c2")

            vm.setQuery("x")
            runCurrent()

            vm.handleEvent(SearchFeedsEvent.LoadMore)
            runCurrent()
            val callsAfterFirstLoadMore = repo.callLog.size
            val statusMid = vm.uiState.value.loadStatus
            assertTrue(statusMid is SearchFeedsLoadStatus.Loaded)
            assertEquals(true, (statusMid as SearchFeedsLoadStatus.Loaded).isAppending)

            vm.handleEvent(SearchFeedsEvent.LoadMore)
            runCurrent()
            assertEquals(
                callsAfterFirstLoadMore,
                repo.callLog.size,
                "concurrent LoadMore must not double-fire the repo",
            )
        }

    @Test
    fun setQuery_rapidChange_cancelsPrior_viaMapLatest() =
        runTest {
            val vm = SearchFeedsViewModel(repo)
            val staleGate = repo.gate(query = "ar", cursor = null)
            repo.respond(
                query = "art",
                cursor = null,
                items = listOf(feedFixture(uri = "at://did:plc:art/app.bsky.feed.generator/art")),
                nextCursor = null,
            )

            vm.setQuery("ar")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchFeedsLoadStatus.InitialLoading)

            vm.setQuery("art")
            runCurrent()

            staleGate.complete(
                Result.success(
                    net.kikin.nubecita.feature.search.impl.data.SearchFeedsPage(
                        items =
                            kotlinx.collections.immutable.persistentListOf(
                                feedFixture(uri = "at://did:plc:stale/app.bsky.feed.generator/stale"),
                            ),
                        nextCursor = null,
                    ),
                ),
            )
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchFeedsLoadStatus.Loaded, "expected Loaded for art, was $status")
            assertEquals(
                "at://did:plc:art/app.bsky.feed.generator/art",
                (status as SearchFeedsLoadStatus.Loaded).items.single().uri,
                "stale 'ar' completion must not clobber 'art' results",
            )
        }

    @Test
    fun loadMore_inFlight_whenQueryChanges_doesNotClobberNewQueryItems() =
        runTest {
            // Regression test for the stale-completion guard. Mirrors the
            // analogous People VM test inherited from vrba.6's review.
            val vm = SearchFeedsViewModel(repo)
            // Page 1 "art": one item + nextCursor=c2 so loadMore is valid.
            repo.respond(
                query = "art",
                cursor = null,
                items = listOf(feedFixture(uri = "at://did:plc:art/app.bsky.feed.generator/art")),
                nextCursor = "c2",
            )
            // Page 1 "books": end-of-results.
            repo.respond(
                query = "books",
                cursor = null,
                items = listOf(feedFixture(uri = "at://did:plc:books/app.bsky.feed.generator/books")),
                nextCursor = null,
            )
            // Gate the page-2-art fetch so we control completion timing.
            val pageTwoArtGate = repo.gate(query = "art", cursor = "c2")

            vm.setQuery("art")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchFeedsLoadStatus.Loaded)

            vm.handleEvent(SearchFeedsEvent.LoadMore)
            runCurrent()
            assertTrue(
                (vm.uiState.value.loadStatus as SearchFeedsLoadStatus.Loaded).isAppending,
                "page-2-art fetch should be in flight",
            )

            // User types past the boundary. mapLatest fires runFirstPage(books).
            vm.setQuery("books")
            runCurrent()
            val afterTyping = vm.uiState.value.loadStatus
            assertTrue(afterTyping is SearchFeedsLoadStatus.Loaded)
            assertEquals(
                "at://did:plc:books/app.bsky.feed.generator/books",
                (afterTyping as SearchFeedsLoadStatus.Loaded).items.single().uri,
                "books results should have landed",
            )

            // Stale page-2-art completion arrives AFTER the query change.
            pageTwoArtGate.complete(
                Result.success(
                    net.kikin.nubecita.feature.search.impl.data.SearchFeedsPage(
                        items =
                            kotlinx.collections.immutable.persistentListOf(
                                feedFixture(uri = "at://did:plc:stale-art/app.bsky.feed.generator/stale"),
                            ),
                        nextCursor = "c3",
                    ),
                ),
            )
            runCurrent()

            val finalStatus = vm.uiState.value.loadStatus
            assertTrue(finalStatus is SearchFeedsLoadStatus.Loaded)
            finalStatus as SearchFeedsLoadStatus.Loaded
            assertEquals(
                listOf("at://did:plc:books/app.bsky.feed.generator/books"),
                finalStatus.items.map { it.uri },
                "stale art-page-2 items must not appear on the books list",
            )
            assertEquals(null, finalStatus.nextCursor, "cursor must remain books' null cursor")
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
