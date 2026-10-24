package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.feature.search.api.Hashtag
import net.kikin.nubecita.feature.search.impl.data.FakeSearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.data.hashtagKey
import net.kikin.nubecita.feature.search.impl.data.searchPostFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [HashtagViewModel]. The hand-written
 * [FakeSearchPostsRepository] routes `searchHashtag` through its gates keyed
 * on [hashtagKey], so `callLog` records the tag/author/sort/cursor of every
 * request the VM makes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HashtagViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeSearchPostsRepository()
    private val cache = FakePostInteractionsCache()
    private val handler = FakePostInteractionHandler(cache)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        tag: String = "Kotlin",
        author: String? = null,
    ) = HashtagViewModel(Hashtag(tag, author), repo, cache, handler)

    private fun hits(vararg ids: String) = ids.map { searchPostFixture(uri = it, text = "post $it") }

    @Test
    fun init_loadsTopForTheTag_withoutAuthor() =
        runTest {
            repo.respond(hashtagKey("Kotlin", null), cursor = null, sort = SearchPostsSort.TOP, items = hits("a", "b"), nextCursor = "c1")

            val vm = buildVm(author = "alice.bsky.social")
            runCurrent()

            val top = vm.uiState.value.top.status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("a", "b"), top.items.map { it.post.id })
            assertFalse(top.endReached)
            // The author is offered as a filter, never applied by default.
            assertEquals(listOf(hashtagKey("Kotlin", null)), repo.callLog.map { it.query })
            assertEquals(SearchPostsLoadStatus.Idle, vm.uiState.value.latest.status)
        }

    @Test
    fun selectingLatest_loadsItLazily_once() =
        runTest {
            repo.respond(hashtagKey("Kotlin", null), cursor = null, sort = SearchPostsSort.LATEST, items = hits("x"))
            val vm = buildVm()
            runCurrent()

            vm.handleEvent(HashtagEvent.TabSelected(SearchPostsSort.LATEST))
            runCurrent()
            vm.handleEvent(HashtagEvent.TabSelected(SearchPostsSort.TOP))
            vm.handleEvent(HashtagEvent.TabSelected(SearchPostsSort.LATEST))
            runCurrent()

            assertEquals(SearchPostsSort.LATEST, vm.uiState.value.selectedSort)
            assertEquals(listOf(SearchPostsSort.TOP, SearchPostsSort.LATEST), repo.callLog.map { it.sort })
            val latest = vm.uiState.value.latest.status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("x"), latest.items.map { it.post.id })
        }

    @Test
    fun loadMore_appendsNextPage_dedupingAndEndingAtNullCursor() =
        runTest {
            val key = hashtagKey("Kotlin", null)
            repo.respond(key, cursor = null, sort = SearchPostsSort.TOP, items = hits("a", "b"), nextCursor = "c1")
            repo.respond(key, cursor = "c1", sort = SearchPostsSort.TOP, items = hits("b", "c"), nextCursor = null)
            val vm = buildVm()
            runCurrent()

            vm.handleEvent(HashtagEvent.LoadMore)
            runCurrent()
            vm.handleEvent(HashtagEvent.LoadMore)
            runCurrent()

            val top = vm.uiState.value.top.status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("a", "b", "c"), top.items.map { it.post.id })
            assertTrue(top.endReached)
            assertEquals(2, repo.callLog.size, "LoadMore past the end must not hit the repo")
        }

    @Test
    fun emptyResult_isEmpty() =
        runTest {
            val vm = buildVm()
            runCurrent()

            assertEquals(SearchPostsLoadStatus.Empty, vm.uiState.value.top.status)
        }

    @Test
    fun initialError_thenRetry_loads() =
        runTest {
            val key = hashtagKey("Kotlin", null)
            repo.fail(key, cursor = null, sort = SearchPostsSort.TOP, throwable = IOException("offline"))
            val vm = buildVm()
            runCurrent()
            assertEquals(SearchPostsLoadStatus.InitialError(SearchPostsError.Network), vm.uiState.value.top.status)

            repo.clearGate(key, cursor = null, sort = SearchPostsSort.TOP)
            repo.respond(key, cursor = null, sort = SearchPostsSort.TOP, items = hits("a"))
            vm.handleEvent(HashtagEvent.Retry)
            runCurrent()

            assertTrue(vm.uiState.value.top.status is SearchPostsLoadStatus.Loaded)
        }

    @Test
    fun authorFilter_reloadsWithAuthor_andResetsOtherTab() =
        runTest {
            repo.respond(hashtagKey("Kotlin", null), cursor = null, sort = SearchPostsSort.TOP, items = hits("a"))
            repo.respond(hashtagKey("Kotlin", null), cursor = null, sort = SearchPostsSort.LATEST, items = hits("b"))
            repo.respond(hashtagKey("Kotlin", "alice.bsky.social"), cursor = null, sort = SearchPostsSort.LATEST, items = hits("mine"))
            val vm = buildVm(author = "alice.bsky.social")
            runCurrent()
            vm.handleEvent(HashtagEvent.TabSelected(SearchPostsSort.LATEST))
            runCurrent()

            vm.handleEvent(HashtagEvent.AuthorFilterToggled)
            runCurrent()

            assertTrue(vm.uiState.value.authorFilterOn)
            val latest = vm.uiState.value.latest.status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("mine"), latest.items.map { it.post.id })
            // Top was reset and reloads (with the filter) only when selected again.
            assertEquals(SearchPostsLoadStatus.Idle, vm.uiState.value.top.status)
            assertEquals(hashtagKey("Kotlin", "alice.bsky.social"), repo.callLog.last().query)
        }

    @Test
    fun authorFilter_isIgnoredWithoutAnAuthor() =
        runTest {
            val vm = buildVm(author = null)
            runCurrent()

            vm.handleEvent(HashtagEvent.AuthorFilterToggled)
            runCurrent()

            assertFalse(vm.uiState.value.authorFilterOn)
            assertEquals(1, repo.callLog.size)
        }

    @Test
    fun staleResponse_afterFilterToggle_isDropped() =
        runTest {
            val unfiltered = repo.gate(hashtagKey("Kotlin", null), cursor = null, sort = SearchPostsSort.TOP)
            repo.respond(hashtagKey("Kotlin", "alice.bsky.social"), cursor = null, sort = SearchPostsSort.TOP, items = hits("mine"))
            val vm = buildVm(author = "alice.bsky.social")
            runCurrent()

            vm.handleEvent(HashtagEvent.AuthorFilterToggled)
            runCurrent()
            unfiltered.complete(
                Result.success(
                    net.kikin.nubecita.feature.search.impl.data
                        .SearchPostsPage(items = kotlinx.collections.immutable.persistentListOf(hits("old").single()), nextCursor = null),
                ),
            )
            runCurrent()

            val top = vm.uiState.value.top.status as SearchPostsLoadStatus.Loaded
            assertEquals(listOf("mine"), top.items.map { it.post.id })
        }

    @Test
    fun refresh_replacesResults() =
        runTest {
            val key = hashtagKey("Kotlin", null)
            repo.respond(key, cursor = null, sort = SearchPostsSort.TOP, items = hits("a"))
            val vm = buildVm()
            runCurrent()

            repo.clearGate(key, cursor = null, sort = SearchPostsSort.TOP)
            repo.respond(key, cursor = null, sort = SearchPostsSort.TOP, items = hits("new", "a"))
            vm.handleEvent(HashtagEvent.Refresh)
            runCurrent()

            val top = vm.uiState.value.top
            assertFalse(top.isRefreshing)
            assertEquals(listOf("new", "a"), (top.status as SearchPostsLoadStatus.Loaded).items.map { it.post.id })
        }

    @Test
    fun share_buildsBskyHashtagUrl_withAuthorOnlyWhenFiltered() =
        runTest {
            repo.respond(hashtagKey("Kotlin", null), cursor = null, sort = SearchPostsSort.TOP, items = hits("a"))
            val vm = buildVm(author = "alice.bsky.social")
            runCurrent()

            vm.effects.test {
                vm.handleEvent(HashtagEvent.ShareClicked)
                assertEquals(HashtagEffect.Share("https://bsky.app/hashtag/Kotlin"), awaitItem())

                vm.handleEvent(HashtagEvent.AuthorFilterToggled)
                vm.handleEvent(HashtagEvent.ShareClicked)
                assertEquals(HashtagEffect.Share("https://bsky.app/hashtag/Kotlin?author=alice.bsky.social"), awaitItem())
            }
        }

    @Test
    fun share_encodesCashtagAndUnicode() =
        runTest {
            val vm = buildVm(tag = "\$BTC")
            runCurrent()

            vm.effects.test {
                vm.handleEvent(HashtagEvent.ShareClicked)
                assertEquals(HashtagEffect.Share("https://bsky.app/hashtag/%24BTC"), awaitItem())
            }
        }

    @Test
    fun tagTapped_inAResult_opensThatTag() =
        runTest {
            val vm = buildVm()
            runCurrent()

            vm.effects.test {
                vm.handleEvent(HashtagEvent.TagTapped("Android", "bob.bsky.social"))
                assertEquals(HashtagEffect.NavigateToHashtag("Android", "bob.bsky.social"), awaitItem())
            }
        }
}
