package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.feature.search.impl.data.FakeSearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsPage
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.data.searchPostFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [SearchPostsViewModel].
 *
 * The VM owns a `MutableStateFlow<FetchKey>` whose `mapLatest` pipeline
 * runs on the test scheduler via `Dispatchers.setMain(UnconfinedTestDispatcher())`.
 * Unlike `SearchViewModelTest` / `ComposerViewModelTypeaheadTest`, no
 * Compose `TextFieldState` / `snapshotFlow` is involved, so we don't
 * need `Snapshot.sendApplyNotifications()` — `runCurrent()` after each
 * `setQuery` / `handleEvent` call is sufficient to drive the pipeline.
 *
 * Repository fake is the hand-written [FakeSearchPostsRepository]; mockk
 * is not in this module's testImplementation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPostsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeSearchPostsRepository()

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
            val vm = SearchPostsViewModel(repo)
            runCurrent()

            assertEquals(SearchPostsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun setQuery_nonBlank_fetchesFirstPage_emitsLoaded() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            val hit = searchPostFixture(uri = "at://did:plc:fake/p1", text = "kotlin")
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(hit),
                nextCursor = "c2",
            )

            vm.setQuery("kotlin")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded, "expected Loaded, was $status")
            status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf(hit), status.items.toList())
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
            assertEquals("kotlin", vm.uiState.value.currentQuery)
        }

    @Test
    fun setQuery_emptyResponse_emitsEmpty() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "no-matches",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = emptyList(),
                nextCursor = null,
            )

            vm.setQuery("no-matches")
            runCurrent()

            assertEquals(SearchPostsLoadStatus.Empty, vm.uiState.value.loadStatus)
        }

    @Test
    fun setQuery_failure_emitsInitialError_withMappedError() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.fail(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                throwable = IOException("disconnected"),
            )

            vm.setQuery("kotlin")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.InitialError, "expected InitialError, was $status")
            assertEquals(
                SearchPostsError.Network,
                (status as SearchPostsLoadStatus.InitialError).error,
            )
        }

    @Test
    fun setQuery_rapidChange_cancelsPrior_viaMapLatest() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            // Gate the "ali" fetch so it never completes — mapLatest must
            // cancel it when "alic" arrives.
            val aliGate =
                repo.gate(query = "ali", cursor = null, sort = SearchPostsSort.TOP)
            repo.respond(
                query = "alic",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "alic")),
                nextCursor = null,
            )

            vm.setQuery("ali")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPostsLoadStatus.InitialLoading)

            vm.setQuery("alic")
            runCurrent()

            // Late completion of the cancelled deferred must NOT clobber
            // the Loaded("alic", ...) that mapLatest wrote.
            aliGate.complete(
                Result.success(
                    SearchPostsPage(
                        items =
                            persistentListOf(
                                searchPostFixture("at://stale", "ali"),
                            ),
                        nextCursor = null,
                    ),
                ),
            )
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded, "expected Loaded for alic, was $status")
            assertEquals(
                "at://p1",
                (status as SearchPostsLoadStatus.Loaded)
                    .items
                    .single()
                    .post.id,
            )
        }

    @Test
    fun loadMore_loaded_appendsNextPage_andClearsIsAppending() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            val page1 = listOf(searchPostFixture("at://p1", "p1"))
            val page2 = listOf(searchPostFixture("at://p2", "p2"), searchPostFixture("at://p3", "p3"))
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = page1,
                nextCursor = "c2",
            )
            repo.respond(
                query = "kotlin",
                cursor = "c2",
                sort = SearchPostsSort.TOP,
                items = page2,
                nextCursor = "c3",
            )

            vm.setQuery("kotlin")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPostsLoadStatus.Loaded)

            vm.handleEvent(SearchPostsEvent.LoadMore)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded)
            status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("at://p1", "at://p2", "at://p3"), status.items.map { it.post.id })
            assertEquals("c3", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_endReached_isNoOp() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "p1")),
                nextCursor = null, // endReached
            )

            vm.setQuery("kotlin")
            runCurrent()
            val beforeCallCount = repo.callLog.size

            vm.handleEvent(SearchPostsEvent.LoadMore)
            runCurrent()

            assertEquals(beforeCallCount, repo.callLog.size, "endReached must short-circuit before hitting repo")
        }

    @Test
    fun loadMore_alreadyAppending_isNoOp_singleFlight() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "p1")),
                nextCursor = "c2",
            )
            // Gate the second-page fetch so isAppending stays true.
            repo.gate(query = "kotlin", cursor = "c2", sort = SearchPostsSort.TOP)

            vm.setQuery("kotlin")
            runCurrent()

            vm.handleEvent(SearchPostsEvent.LoadMore)
            runCurrent()
            val callsAfterFirstLoadMore = repo.callLog.size
            val statusMid = vm.uiState.value.loadStatus
            assertTrue(statusMid is SearchPostsLoadStatus.Loaded)
            assertEquals(true, (statusMid as SearchPostsLoadStatus.Loaded).isAppending)

            // Second LoadMore while the first is in flight — should be dropped.
            vm.handleEvent(SearchPostsEvent.LoadMore)
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
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://p1", "p1")),
                nextCursor = "c2",
            )
            repo.fail(
                query = "kotlin",
                cursor = "c2",
                sort = SearchPostsSort.TOP,
                throwable = IOException("flap"),
            )

            vm.setQuery("kotlin")
            runCurrent()

            vm.effects.test {
                vm.handleEvent(SearchPostsEvent.LoadMore)
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchPostsEffect.ShowAppendError)
                assertEquals(SearchPostsError.Network, (effect as SearchPostsEffect.ShowAppendError).error)
                cancelAndIgnoreRemainingEvents()
            }

            // Existing items still visible, isAppending cleared.
            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded)
            status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("at://p1"), status.items.map { it.post.id })
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun sortClicked_resetsPaginationAndFetches_freshFirstPage() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://top1", "top1")),
                nextCursor = "c2",
            )
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.LATEST,
                items = listOf(searchPostFixture("at://latest1", "latest1")),
                nextCursor = null,
            )

            vm.setQuery("kotlin")
            runCurrent()
            val firstStatus = vm.uiState.value.loadStatus as SearchPostsLoadStatus.Loaded
            assertEquals(
                "at://top1",
                firstStatus.items
                    .single()
                    .post.id,
            )

            vm.handleEvent(SearchPostsEvent.SortClicked(SearchPostsSort.LATEST))
            runCurrent()

            val secondStatus = vm.uiState.value.loadStatus
            assertTrue(secondStatus is SearchPostsLoadStatus.Loaded)
            assertEquals(
                "at://latest1",
                (secondStatus as SearchPostsLoadStatus.Loaded)
                    .items
                    .single()
                    .post.id,
            )
            assertEquals(SearchPostsSort.LATEST, vm.uiState.value.sort)
        }

    @Test
    fun retry_initialError_retriggersFirstPage_viaIncarnationBump() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            // First call fails.
            repo.fail(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                throwable = IOException("network"),
            )

            vm.setQuery("kotlin")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchPostsLoadStatus.InitialError)

            // The previous gate has been completed with a failure. Clear
            // it and register a fresh success so the bumped-incarnation
            // refetch returns Loaded.
            repo.clearGate(query = "kotlin", cursor = null, sort = SearchPostsSort.TOP)
            repo.respond(
                query = "kotlin",
                cursor = null,
                sort = SearchPostsSort.TOP,
                items = listOf(searchPostFixture("at://retry-success", "yay")),
                nextCursor = null,
            )

            vm.handleEvent(SearchPostsEvent.Retry)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchPostsLoadStatus.Loaded, "expected Loaded after retry, was $status")
        }

    @Test
    fun postTapped_emitsNavigateToPostEffect() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            vm.effects.test {
                vm.handleEvent(SearchPostsEvent.PostTapped("at://did:plc:fake/p1"))
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchPostsEffect.NavigateToPost)
                assertEquals("at://did:plc:fake/p1", (effect as SearchPostsEffect.NavigateToPost).uri)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearQueryClicked_emitsNavigateToClearQueryEffect() =
        runTest {
            val vm = SearchPostsViewModel(repo)
            vm.effects.test {
                vm.handleEvent(SearchPostsEvent.ClearQueryClicked)
                runCurrent()

                assertEquals(SearchPostsEffect.NavigateToClearQuery, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
