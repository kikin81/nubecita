package net.kikin.nubecita.feature.search.impl.pane

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.search.impl.FakeFollowRepository
import net.kikin.nubecita.feature.search.impl.FakePinnedFeedsRepository
import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.FakeSearchActorsRepository
import net.kikin.nubecita.feature.search.impl.data.FakeSearchFeedsRepository
import net.kikin.nubecita.feature.search.impl.data.FakeSearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.FeedPinToggle
import net.kikin.nubecita.feature.search.impl.data.FollowOverlay
import net.kikin.nubecita.feature.search.impl.data.FollowOverride
import net.kikin.nubecita.feature.search.impl.data.FollowToggle
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.ReplyFilter
import net.kikin.nubecita.feature.search.impl.data.SearchFirstPages
import net.kikin.nubecita.feature.search.impl.data.SearchPostsPage
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.data.SearchTab
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearchStore
import net.kikin.nubecita.feature.search.impl.data.actorFixture
import net.kikin.nubecita.feature.search.impl.data.feedFixture
import net.kikin.nubecita.feature.search.impl.data.searchPostFixture
import net.kikin.nubecita.feature.search.impl.data.seed
import net.kikin.nubecita.feature.search.impl.data.signedIn
import net.kikin.nubecita.feature.search.impl.ui.shownHitsTotal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class SearchPaneViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val store = SubmittedSearchStore()
    private val posts = FakeSearchPostsRepository()
    private val actors = FakeSearchActorsRepository()
    private val feeds = FakeSearchFeedsRepository()
    private val pinned = FakePinnedFeedsRepository()
    private val followRepo = FakeFollowRepository()
    private val session = MutableStateFlow<SessionState>(signedIn("did:plc:me"))
    private val sessionProvider =
        object : SessionStateProvider {
            override val state = session

            override suspend fun refresh() = Unit
        }

    /** The shared first pages of the VM under test, so a test can play the part of a result tab. */
    private lateinit var firstPages: SearchFirstPages
    private lateinit var overlay: FollowOverlay

    private fun TestScope.buildVm(): SearchPaneViewModel {
        // Not `backgroundScope`: `advanceUntilIdle` does not run background tasks, and the shared
        // first pages and the follow call are real coroutines the tests wait for.
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        firstPages = SearchFirstPages(posts, actors, feeds, appScope, sessionProvider)
        overlay = FollowOverlay(appScope, sessionProvider)
        return SearchPaneViewModel(
            store = store,
            firstPages = firstPages,
            followToggle = FollowToggle(followRepo, overlay, sessionProvider, appScope),
            followOverlay = overlay,
            pinToggle = FeedPinToggle(pinned),
            pinnedFeedsRepository = pinned,
            sessionStateProvider = sessionProvider,
        )
    }

    private fun summary(vm: SearchPaneViewModel) = vm.uiState.value.mode as SearchPaneMode.Summary

    private fun respondPosts(
        query: String,
        vararg uris: String,
        hitsTotal: Long? = null,
        sort: SearchPostsSort = SearchPostsSort.TOP,
    ) = posts.respond(query, null, sort, uris.map { searchPostFixture(it, "text of $it") }, hitsTotal = hitsTotal)

    @Test
    fun `mode is Explore with no search`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()

            assertEquals(SearchPaneMode.Explore, vm.uiState.value.mode)
        }

    @Test
    fun `mode is Summary after a submit`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            val mode = vm.uiState.value.mode
            assertTrue(mode is SearchPaneMode.Summary)
            assertEquals("kotlin", (mode as SearchPaneMode.Summary).search.query)
        }

    @Test
    fun `mode returns to Explore after clear`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            store.clear()
            advanceUntilIdle()

            assertEquals(SearchPaneMode.Explore, vm.uiState.value.mode)
        }

    @Test
    fun `ScrollSaved stores under the current mode only`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(3, 40)))
            assertEquals(ScrollPosition(3, 40), vm.uiState.value.exploreScroll)
            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            assertEquals(ScrollPosition(3, 40), vm.uiState.value.exploreScroll)
            assertEquals(ScrollPosition(7, 5), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `ScrollSaved with an explicit target stores under that mode even when the mode has moved on`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            // The Explore list leaving composition after a search was submitted.
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(2, 9), ScrollTarget.Explore))

            assertEquals(ScrollPosition(2, 9), vm.uiState.value.exploreScroll)
            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `explore scroll survives a search and its clearing`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(3, 40)))

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            store.clear()
            advanceUntilIdle()

            assertEquals(ScrollPosition(3, 40), vm.uiState.value.exploreScroll)
        }

    @Test
    fun `summary scroll resets when the query changes`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `summary scroll resets when a filter changes`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            store.updateFilters { it.copy(media = MediaFilter.HAS_MEDIA) }
            advanceUntilIdle()

            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `summary scroll is kept when only the selected tab changes`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            store.selectTab(SearchTab.PEOPLE)
            advanceUntilIdle()

            assertEquals(ScrollPosition(7, 5), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `section expansion toggles flip independently and survive a mode change`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.FeedsExpansionToggled)
            assertTrue(vm.uiState.value.feedsExpanded)
            assertEquals(false, vm.uiState.value.accountsExpanded)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            store.clear()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.feedsExpanded)

            vm.handleEvent(SearchPaneEvent.FeedsExpansionToggled)
            vm.handleEvent(SearchPaneEvent.AccountsExpansionToggled)
            assertEquals(false, vm.uiState.value.feedsExpanded)
            assertTrue(vm.uiState.value.accountsExpanded)
        }

    @Test
    fun `the summary key changes with the query and with the filters but not with the selected tab`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()
            assertEquals(null, vm.uiState.value.summaryKey)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            val first = vm.uiState.value.summaryKey
            assertEquals("kotlin", first?.query)

            store.selectTab(SearchTab.PEOPLE)
            store.setFilterSheetOpen(true)
            advanceUntilIdle()
            assertEquals(first, vm.uiState.value.summaryKey)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            assertEquals(first, vm.uiState.value.summaryKey)

            store.updateFilters { it.copy(media = MediaFilter.HAS_MEDIA) }
            advanceUntilIdle()
            val afterFilter = vm.uiState.value.summaryKey
            assertTrue(afterFilter != first)

            store.submit("compose", fromRecent = false)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.summaryKey != afterFilter)

            store.clear()
            advanceUntilIdle()
            assertEquals(null, vm.uiState.value.summaryKey)
        }

    @Test
    fun `a summary scroll save for a stale key is ignored`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            val staleKey = vm.uiState.value.summaryKey
            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            // The old list leaving composition after the search changed must not undo the reset.
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(9, 9), ScrollTarget.Summary, staleKey))
            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)

            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(4, 2), ScrollTarget.Summary, vm.uiState.value.summaryKey))
            assertEquals(ScrollPosition(4, 2), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `summary loads the three first pages once per search key`() =
        runTest {
            respondPosts("kotlin", "at://p/1", "at://p/2", hitsTotal = 1240)
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a"), actorFixture("did:plc:b")))
            feeds.respond("kotlin", null, listOf(feedFixture(uri = "at://f/1")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            val mode = summary(vm)
            assertEquals("at://p/1", mode.topPost?.post?.id)
            assertEquals(1240L, mode.hitsTotal)
            assertEquals(listOf("did:plc:a", "did:plc:b"), mode.people.map { it.did })
            assertEquals(listOf("at://f/1"), mode.feeds.map { it.uri })
            assertEquals(SummarySectionStatus.Loaded, mode.postsStatus)
            assertEquals(SummarySectionStatus.Loaded, mode.peopleStatus)
            assertEquals(SummarySectionStatus.Loaded, mode.feedsStatus)
            assertEquals(1, posts.callLog.size)
            assertEquals(1, actors.callLog.size)
            assertEquals(1, feeds.callLog.size)

            // Neither the tab nor the sheet flag is a new search.
            store.selectTab(SearchTab.PEOPLE)
            store.setFilterSheetOpen(true)
            advanceUntilIdle()
            assertEquals(1, posts.callLog.size)
            assertEquals(1, actors.callLog.size)
            assertEquals(1, feeds.callLog.size)
        }

    @Test
    fun `people and feeds show at most three rows`() =
        runTest {
            actors.respond("kotlin", null, (1..5).map { actorFixture("did:plc:$it") })
            feeds.respond("kotlin", null, (1..5).map { feedFixture(uri = "at://f/$it") })
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            assertEquals(3, summary(vm).people.size)
            assertEquals(3, summary(vm).feeds.size)
        }

    @Test
    fun `a tab loading the same search does not cause a second request`() =
        runTest {
            respondPosts("kotlin", "at://p/1")
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            val search = store.submitted.value!!

            // What the three tabs do when the pager reaches them.
            firstPages.posts(search.query, search.filters, search.submission)
            firstPages.people(search.query, search.submission)
            firstPages.feeds(search.query, search.submission)
            advanceUntilIdle()

            assertEquals(1, posts.callLog.size)
            assertEquals(1, actors.callLog.size)
            assertEquals(1, feeds.callLog.size)
            assertEquals("at://p/1", summary(vm).topPost?.post?.id)
        }

    @Test
    fun `a tab pull-to-refresh updates the summary`() =
        runTest {
            respondPosts("kotlin", "at://p/old", hitsTotal = 10)
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            assertEquals("at://p/old", summary(vm).topPost?.post?.id)

            posts.clearGate("kotlin", null, SearchPostsSort.TOP)
            respondPosts("kotlin", "at://p/new", hitsTotal = 11)
            val search = store.submitted.value!!
            firstPages.posts(search.query, search.filters, search.submission, refresh = true)
            advanceUntilIdle()

            assertEquals("at://p/new", summary(vm).topPost?.post?.id)
            assertEquals(11L, summary(vm).hitsTotal)
            assertEquals(SummarySectionStatus.Loaded, summary(vm).postsStatus)
            // The refresh is the tab's one request; the summary read it from the cache.
            assertEquals(2, posts.callLog.size)
        }

    @Test
    fun `a result for an old key never reaches the new search`() =
        runTest {
            // The first search never answers until the second has been shown.
            val oldPosts = posts.gate("kotlin", null, SearchPostsSort.TOP)
            val oldPeople = actors.gate("kotlin", null)
            respondPosts("compose", "at://p/compose")
            actors.respond("compose", null, listOf(actorFixture("did:plc:compose")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            oldPosts.complete(
                Result.success(
                    SearchPostsPage(
                        items =
                            persistentListOf(searchPostFixture("at://p/kotlin", "old")),
                        nextCursor = null,
                        hitsTotal = 999,
                    ),
                ),
            )
            oldPeople.complete(
                Result.success(
                    ActorSearchPage(
                        items = persistentListOf(actorFixture("did:plc:kotlin")),
                        nextCursor = null,
                    ),
                ),
            )
            advanceUntilIdle()

            val mode = summary(vm)
            assertEquals("compose", mode.search.query)
            assertEquals("at://p/compose", mode.topPost?.post?.id)
            assertNull(mode.hitsTotal)
            assertEquals(listOf("did:plc:compose"), mode.people.map { it.did })
        }

    @Test
    fun `a page that arrives for the previous search after a new one was adopted is dropped`() =
        runTest {
            val oldPeople = actors.gate("kotlin", null)
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            // No scheduler advance between these: the old answer and the new search land in the same
            // round of work, before the old request has been cancelled.
            oldPeople.complete(Result.success(ActorSearchPage(persistentListOf(actorFixture("did:plc:kotlin")), null)))
            actors.fail("compose", null, IOException("offline"))
            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            val mode = summary(vm)
            assertEquals("compose", mode.search.query)
            assertEquals(SummarySectionStatus.Error, mode.peopleStatus)
            assertTrue(mode.people.isEmpty())
        }

    @Test
    fun `a page fetched under the previous account is dropped`() =
        runTest {
            val oldPeople = actors.gate("kotlin", null)
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            actors.clearGate("kotlin", null)
            actors.fail("kotlin", null, IOException("offline"))
            oldPeople.complete(Result.success(ActorSearchPage(persistentListOf(actorFixture("did:plc:previous")), null)))
            session.value = signedIn("did:plc:other")
            advanceUntilIdle()

            assertEquals(SummarySectionStatus.Error, summary(vm).peopleStatus)
            assertTrue(summary(vm).people.isEmpty())
        }

    @Test
    fun `a new search clears the previous results at once`() =
        runTest {
            respondPosts("kotlin", "at://p/1", hitsTotal = 5)
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a")))
            feeds.respond("kotlin", null, listOf(feedFixture(uri = "at://f/1")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            // "compose" never answers: nothing of "kotlin" may remain under its header.
            posts.gate("compose", null, SearchPostsSort.TOP)
            actors.gate("compose", null)
            feeds.gate("compose", null)
            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            val mode = summary(vm)
            assertNull(mode.topPost)
            assertNull(mode.hitsTotal)
            assertTrue(mode.people.isEmpty())
            assertTrue(mode.feeds.isEmpty())
            assertEquals(SummarySectionStatus.Loading, mode.postsStatus)
            assertEquals(SummarySectionStatus.Loading, mode.peopleStatus)
            assertEquals(SummarySectionStatus.Loading, mode.feedsStatus)
        }

    @Test
    fun `a filter change reloads the posts only and never sends people or feeds back to loading`() =
        runTest {
            respondPosts("kotlin", "at://p/1")
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a")))
            feeds.respond("kotlin", null, listOf(feedFixture(uri = "at://f/1")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            posts.gate("kotlin", null, SearchPostsSort.LATEST)
            store.updateFilters { it.copy(sort = SearchPostsSort.LATEST) }
            runCurrent()
            // The new posts request is gated, so this is the state the filter change produced:
            val mode = summary(vm)
            assertEquals(SummarySectionStatus.Loading, mode.postsStatus)
            assertNull(mode.topPost)
            assertEquals(SummarySectionStatus.Loaded, mode.peopleStatus)
            assertEquals(SummarySectionStatus.Loaded, mode.feedsStatus)
            assertEquals(listOf("did:plc:a"), mode.people.map { it.did })

            advanceUntilIdle()
            assertEquals(2, posts.callLog.size)
            assertEquals(
                SearchPostsSort.LATEST,
                posts.callLog
                    .last()
                    .filters.sort,
            )
            assertEquals(1, actors.callLog.size)
            assertEquals(1, feeds.callLog.size)
            assertEquals(SummarySectionStatus.Loaded, summary(vm).peopleStatus)
        }

    @Test
    fun `a search whose first pages are already cached ends with all three sections loaded`() =
        runTest {
            respondPosts("kotlin", "at://p/1")
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            // A tab already loaded the next search: its pages are cached, so the pane's fetches
            // return without suspending and can finish before the pane has even adopted the key.
            respondPosts("compose", "at://p/compose")
            actors.respond("compose", null, listOf(actorFixture("did:plc:compose")))
            feeds.respond("compose", null, listOf(feedFixture(uri = "at://f/compose")))
            val next = store.submitted.value!!.submission + 1
            firstPages.posts("compose", PostSearchFilters(), next)
            firstPages.people("compose", next)
            firstPages.feeds("compose", next)
            advanceUntilIdle()

            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            val mode = summary(vm)
            assertEquals(next, mode.search.submission)
            assertEquals(SummarySectionStatus.Loaded, mode.postsStatus)
            assertEquals(SummarySectionStatus.Loaded, mode.peopleStatus)
            assertEquals(SummarySectionStatus.Loaded, mode.feedsStatus)
            assertEquals("at://p/compose", mode.topPost?.post?.id)
            assertEquals(listOf("did:plc:compose"), mode.people.map { it.did })
            assertEquals(1, posts.callLog.count { it.query == "compose" })
        }

    @Test
    fun `an account change clears the summary sections even if the re-read fails`() =
        runTest {
            respondPosts("kotlin", "at://p/1", hitsTotal = 5)
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a")))
            feeds.respond("kotlin", null, listOf(feedFixture(uri = "at://f/1")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            assertEquals(1, summary(vm).people.size)

            // The next account's re-read fails everywhere.
            posts.clearGate("kotlin", null, SearchPostsSort.TOP)
            actors.clearGate("kotlin", null)
            feeds.clearGate("kotlin", null)
            posts.fail("kotlin", null, SearchPostsSort.TOP, IOException("offline"))
            actors.fail("kotlin", null, IOException("offline"))
            feeds.fail("kotlin", null, IOException("offline"))
            session.value = signedIn("did:plc:other")
            advanceUntilIdle()

            val mode = summary(vm)
            assertEquals(SummarySectionStatus.Error, mode.postsStatus)
            assertEquals(SummarySectionStatus.Error, mode.peopleStatus)
            assertEquals(SummarySectionStatus.Error, mode.feedsStatus)
            assertNull(mode.topPost)
            assertNull(mode.hitsTotal)
            assertTrue(mode.people.isEmpty())
            assertTrue(mode.feeds.isEmpty())
        }

    @Test
    fun `a failed re-read for the same account keeps the loaded rows`() =
        runTest {
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            // A tab's pull-to-refresh fails: the cache bumps its version, the pane re-reads and fails.
            actors.clearGate("kotlin", null)
            actors.fail("kotlin", null, IOException("offline"))
            val search = store.submitted.value!!
            firstPages.people(search.query, search.submission, refresh = true)
            advanceUntilIdle()

            assertEquals(SummarySectionStatus.Loaded, summary(vm).peopleStatus)
            assertEquals(listOf("did:plc:a"), summary(vm).people.map { it.did })
        }

    @Test
    fun `an explicit re-submit of the same text is a new search`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            assertEquals(2, posts.callLog.size)
            assertEquals(2, actors.callLog.size)
            assertEquals(2, feeds.callLog.size)
            assertEquals(SummarySectionStatus.Loaded, summary(vm).postsStatus)
        }

    @Test
    fun `one section failing leaves the others loaded and RetrySection reloads only it`() =
        runTest {
            respondPosts("kotlin", "at://p/1")
            actors.fail("kotlin", null, IOException("offline"))
            feeds.respond("kotlin", null, listOf(feedFixture(uri = "at://f/1")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            assertEquals(SummarySectionStatus.Error, summary(vm).peopleStatus)
            assertEquals(SummarySectionStatus.Loaded, summary(vm).postsStatus)
            assertEquals(SummarySectionStatus.Loaded, summary(vm).feedsStatus)
            assertEquals(1, posts.callLog.size)
            assertEquals(1, feeds.callLog.size)

            actors.clearGate("kotlin", null)
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a")))
            vm.handleEvent(SearchPaneEvent.RetrySection(SummarySection.People))
            assertEquals(SummarySectionStatus.Loading, summary(vm).peopleStatus)
            advanceUntilIdle()

            assertEquals(SummarySectionStatus.Loaded, summary(vm).peopleStatus)
            assertEquals(listOf("did:plc:a"), summary(vm).people.map { it.did })
            assertEquals(2, actors.callLog.size)
            assertEquals(1, posts.callLog.size)
            assertEquals(1, feeds.callLog.size)
        }

    @Test
    fun `an empty section is loaded and empty`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            val mode = summary(vm)
            assertNull(mode.topPost)
            assertTrue(mode.people.isEmpty())
            assertTrue(mode.feeds.isEmpty())
            assertEquals(SummarySectionStatus.Loaded, mode.postsStatus)
            assertEquals(SummarySectionStatus.Loaded, mode.peopleStatus)
            assertEquals(SummarySectionStatus.Loaded, mode.feedsStatus)
        }

    @Test
    fun `null hitsTotal omits the count`() =
        runTest {
            respondPosts("kotlin", "at://p/1", hitsTotal = null)
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            assertNull(summary(vm).hitsTotal)
            // The one rule the Posts tab uses to decide whether a count is shown.
            assertNull(shownHitsTotal(summary(vm).hitsTotal))
            assertNull(shownHitsTotal(0L))
            assertEquals(5L, shownHitsTotal(5L))
        }

    @Test
    fun `FilterRemoved updates the store and does not change the tab`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            store.selectTab(SearchTab.PEOPLE)
            store.updateFilters { it.copy(media = MediaFilter.HAS_MEDIA, followingOnly = true) }
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.FilterRemoved(SummaryFilter.Media))
            advanceUntilIdle()

            val search = store.submitted.value!!
            assertEquals(MediaFilter.ANY, search.filters.media)
            assertTrue(search.filters.followingOnly)
            assertEquals(SearchTab.PEOPLE, search.selectedTab)
            assertFalse(search.isFilterSheetOpen)
        }

    @Test
    fun `SeeAllClicked selects that tab`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.SeeAllClicked(SearchTab.FEEDS))

            assertEquals(SearchTab.FEEDS, store.submitted.value?.selectedTab)
        }

    @Test
    fun `EditFiltersClicked opens the sheet`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            store.selectTab(SearchTab.PEOPLE)
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.EditFiltersClicked)

            assertTrue(store.submitted.value!!.isFilterSheetOpen)
            assertEquals(SearchTab.PEOPLE, store.submitted.value?.selectedTab)
        }

    @Test
    fun `follow goes through the shared toggle and the viewer's own row is ignored`() =
        runTest {
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a"), actorFixture("did:plc:me")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.FollowTapped("did:plc:me"))
            vm.handleEvent(SearchPaneEvent.FollowTapped("did:plc:a"))
            advanceUntilIdle()

            assertEquals(listOf("did:plc:a"), followRepo.followCallDids)
            val row = summary(vm).people.first { it.did == "did:plc:a" }
            assertTrue(row.isFollowing)
            assertEquals("at://did:plc:me/app.bsky.graph.follow/new", row.followUri)
        }

    @Test
    fun `a failed follow flips the row back and tells the screen`() =
        runTest {
            followRepo.followResult = Result.failure(IOException("offline"))
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.FollowTapped("did:plc:a"))
            advanceUntilIdle()

            assertFalse(summary(vm).people.single().isFollowing)
            assertEquals(SearchPaneEffect.FollowFailed(wasUnfollow = false), vm.effects.first())
        }

    @Test
    fun `a follow made elsewhere shows on the summary's rows`() =
        runTest {
            actors.respond("kotlin", null, listOf(actorFixture("did:plc:a")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            assertFalse(summary(vm).people.single().isFollowing)

            // The People tab (or Explore) followed the same account through the shared overlay.
            overlay.seed("did:plc:a", FollowOverride(isFollowing = true, followUri = "at://follow/1"))
            advanceUntilIdle()

            val row = summary(vm).people.single()
            assertTrue(row.isFollowing)
            assertEquals("at://follow/1", row.followUri)
        }

    @Test
    fun `pin goes through the shared toggle and the pinned stream is applied`() =
        runTest {
            pinned.setPinnedUris(setOf("at://f/2"))
            feeds.respond("kotlin", null, listOf(feedFixture(uri = "at://f/1"), feedFixture(uri = "at://f/2")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            assertEquals(listOf(false, true), summary(vm).feeds.map { it.isPinned })

            vm.handleEvent(SearchPaneEvent.PinTapped("at://f/1"))
            advanceUntilIdle()

            assertEquals(listOf("at://f/1"), pinned.pinCallUris)
            assertTrue(summary(vm).feeds.first().isPinned)

            pinned.setPinnedUris(setOf("at://f/1", "at://f/2"))
            advanceUntilIdle()
            assertEquals(listOf(true, true), summary(vm).feeds.map { it.isPinned })
        }

    @Test
    fun `a failed pin flips the row back and tells the screen`() =
        runTest {
            pinned.pinResult = Result.failure(IOException("offline"))
            feeds.respond("kotlin", null, listOf(feedFixture(uri = "at://f/1")))
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.PinTapped("at://f/1"))
            advanceUntilIdle()

            assertFalse(summary(vm).feeds.single().isPinned)
            assertEquals(SearchPaneEffect.PinFailed(wasUnpin = false), vm.effects.first())
        }

    @Test
    fun `row taps emit navigation effects`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.PostTapped("at://p/1"))
            vm.handleEvent(SearchPaneEvent.ActorTapped("alice.bsky.social"))
            vm.handleEvent(SearchPaneEvent.FeedTapped("at://f/1", "Feed"))

            assertEquals(
                listOf(
                    SearchPaneEffect.NavigateToPost("at://p/1"),
                    SearchPaneEffect.NavigateToProfile("alice.bsky.social"),
                    SearchPaneEffect.NavigateToFeed("at://f/1", "Feed"),
                ),
                vm.effects.take(3).toList(),
            )
        }

    @Test
    fun `active summary filters follow the filter row's rules`() {
        val none =
            net.kikin.nubecita.feature.search.impl.data
                .PostSearchFilters()
        assertEquals(emptyList<SummaryFilter>(), none.activeSummaryFilters())

        val all =
            none.copy(
                sort = SearchPostsSort.LATEST,
                followingOnly = true,
                media = MediaFilter.VIDEO_ONLY,
                replies = ReplyFilter.HIDE,
                language = "en",
                date = DateFilter.PastWeek,
            )
        assertEquals(
            listOf(
                SummaryFilter.Sort,
                SummaryFilter.Following,
                SummaryFilter.Video,
                SummaryFilter.Replies,
                SummaryFilter.Language,
                SummaryFilter.Date,
            ),
            all.activeSummaryFilters(),
        )
        assertEquals(listOf(SummaryFilter.Media), none.copy(media = MediaFilter.HAS_MEDIA).activeSummaryFilters())
    }

    @Test
    fun `removing a filter puts only that filter back to its default`() {
        val filters =
            PostSearchFilters(
                sort = SearchPostsSort.LATEST,
                followingOnly = true,
                media = MediaFilter.VIDEO_ONLY,
                replies = ReplyFilter.ONLY,
                language = "en",
                date = DateFilter.PastDay,
            )
        for (filter in filters.activeSummaryFilters()) {
            val removed = filters.without(filter)
            assertFalse(filter in removed.activeSummaryFilters(), "$filter should be gone")
            assertEquals(filters.activeSummaryFilters() - filter, removed.activeSummaryFilters())
        }
    }
}
