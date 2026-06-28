package net.kikin.nubecita.feature.feed.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository.Companion.FOLLOWING_FEED_URI
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedHostViewModelTest {
    // Share the test dispatcher between Dispatchers.Main (set by the
    // extension) and runTest so the VM's init { observeAndRefresh() } coroutine —
    // launched on viewModelScope (Main) — is driven by the same scheduler
    // advanceUntilIdle() controls. Mirrors FeedViewModelTest's harness.
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val following =
        PinnedFeedUi(
            id = "following",
            uri = FOLLOWING_FEED_URI,
            kind = FeedKind.Following,
            displayName = "Following",
            avatarUrl = null,
        )
    private val art =
        PinnedFeedUi(
            id = "art",
            uri = "at://did:plc:x/app.bsky.feed.generator/art",
            kind = FeedKind.Generator,
            displayName = "Art",
            avatarUrl = null,
        )
    private val discover =
        PinnedFeedUi(
            id = "discover",
            uri = "at://did:plc:x/app.bsky.feed.generator/whats-hot",
            kind = FeedKind.Generator,
            displayName = "Discover",
            avatarUrl = null,
        )
    private val friendsList =
        PinnedFeedUi(
            id = "friends",
            uri = "at://did:plc:x/app.bsky.graph.list/friends",
            kind = FeedKind.List,
            displayName = "Friends",
            avatarUrl = null,
        )

    /**
     * Builds a VM with mocked repos.
     *
     * [pinnedFeedsRepository.observePinnedFeeds] emits a single [PinnedFeedsResult].
     * [pinnedFeedsRepository.refresh] is a no-op success (the DB is mocked so
     * no new emissions come from the DAO).
     * [pinnedFeedsRepository.validateSelectedFeedUri] mirrors the real validation.
     */
    private fun buildVm(
        feeds: List<PinnedFeedUi>,
        usedFallback: Boolean = false,
        persisted: String? = null,
    ): Triple<FeedHostViewModel, PinnedFeedsRepository, UserPreferencesRepository> {
        val pinnedRepo = mockk<PinnedFeedsRepository>()
        every { pinnedRepo.observePinnedFeeds() } returns
            flowOf(PinnedFeedsResult(feeds = feeds.toImmutableList(), usedFallback = usedFallback))
        coEvery { pinnedRepo.refresh() } returns Result.success(Unit)
        every { pinnedRepo.validateSelectedFeedUri(any(), any()) } answers {
            val candidate = firstArg<String?>()
            val pinned = secondArg<List<PinnedFeedUi>>()
            if (candidate != null && pinned.any { it.uri == candidate }) candidate else FOLLOWING_FEED_URI
        }
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { prefs.lastSelectedFeedUri } returns flowOf(persisted)
        val vm = FeedHostViewModel(pinnedRepo, prefs)
        return Triple(vm, pinnedRepo, prefs)
    }

    @Test
    fun `load success splits feeds and lists and reaches Ready`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, _, _) = buildVm(feeds = listOf(following, art, friendsList))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.status is FeedHostStatus.Ready)
            assertEquals(listOf(following, art), state.feedChips)
            assertEquals(listOf(friendsList), state.pinnedLists)
        }

    @Test
    fun `load fallback reaches ErrorFallback and emits ShowError but keeps chips`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, _, _) =
                buildVm(feeds = listOf(following, discover), usedFallback = true)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.status is FeedHostStatus.ErrorFallback)
            assertEquals(listOf(following, discover), vm.uiState.value.feedChips)

            vm.effects.test {
                assertTrue(awaitItem() is FeedHostEffect.ShowError)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `restore uses persisted uri when still pinned`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, _, _) =
                buildVm(feeds = listOf(following, art), persisted = art.uri)
            advanceUntilIdle()

            assertEquals(art.uri, vm.uiState.value.selectedFeedUri)
        }

    @Test
    fun `restore falls back to Following when persisted uri is stale`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, _, _) =
                buildVm(
                    feeds = listOf(following, art),
                    persisted = "at://did:plc:x/app.bsky.feed.generator/gone",
                )
            advanceUntilIdle()

            assertEquals(FOLLOWING_FEED_URI, vm.uiState.value.selectedFeedUri)
        }

    @Test
    fun `SelectFeed updates selection and persists it`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, _, prefs) = buildVm(feeds = listOf(following, art))
            advanceUntilIdle()

            vm.handleEvent(FeedHostEvent.SelectFeed(art.uri))
            advanceUntilIdle()

            assertEquals(art.uri, vm.uiState.value.selectedFeedUri)
            coVerify { prefs.setLastSelectedFeedUri(art.uri) }
        }

    @Test
    fun `SelectList activates the list and persists it`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, _, prefs) = buildVm(feeds = listOf(following, friendsList))
            advanceUntilIdle()

            vm.handleEvent(FeedHostEvent.SelectList(friendsList.uri))
            advanceUntilIdle()

            assertEquals(friendsList.uri, vm.uiState.value.selectedFeedUri)
            coVerify { prefs.setLastSelectedFeedUri(friendsList.uri) }
        }

    @Test
    fun `re-selecting the active feed does not re-persist`() =
        runTest(mainDispatcher.dispatcher) {
            // art is restored as the active feed; re-selecting it is a no-op.
            val (vm, _, prefs) = buildVm(feeds = listOf(following, art), persisted = art.uri)
            advanceUntilIdle()

            vm.handleEvent(FeedHostEvent.SelectFeed(art.uri))
            advanceUntilIdle()

            coVerify(exactly = 0) { prefs.setLastSelectedFeedUri(any()) }
        }

    @Test
    fun `SelectFeed with unpinned uri does not update selection or persist it`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, _, prefs) = buildVm(feeds = listOf(following, art))
            advanceUntilIdle()

            val unpinnedUri = "at://did:plc:x/app.bsky.feed.generator/unpinned"
            vm.handleEvent(FeedHostEvent.SelectFeed(unpinnedUri))
            advanceUntilIdle()

            assertEquals(FOLLOWING_FEED_URI, vm.uiState.value.selectedFeedUri)
            coVerify(exactly = 0) { prefs.setLastSelectedFeedUri(any()) }
        }

    @Test
    fun `Retry triggers a new refresh without resetting selected feed`() =
        runTest(mainDispatcher.dispatcher) {
            val (vm, pinnedRepo, _) = buildVm(feeds = listOf(following, art), persisted = art.uri)
            advanceUntilIdle()

            assertEquals(art.uri, vm.uiState.value.selectedFeedUri)

            vm.handleEvent(FeedHostEvent.Retry)
            advanceUntilIdle()

            // refresh() is called a second time on Retry.
            coVerify(atLeast = 2) { pinnedRepo.refresh() }
            // Selection is preserved.
            assertEquals(art.uri, vm.uiState.value.selectedFeedUri)
        }
}
