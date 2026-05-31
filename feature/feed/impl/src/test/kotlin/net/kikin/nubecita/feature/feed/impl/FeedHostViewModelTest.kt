package net.kikin.nubecita.feature.feed.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository.Companion.FOLLOWING_FEED_URI
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ExtendWith(MainDispatcherExtension::class)
class FeedHostViewModelTest {
    private val following =
        PinnedFeedUi(
            id = "following",
            uri = FOLLOWING_FEED_URI,
            kind = FeedKind.Following,
            displayName = "Following",
        )
    private val art =
        PinnedFeedUi(
            id = "art",
            uri = "at://did:plc:x/app.bsky.feed.generator/art",
            kind = FeedKind.Generator,
            displayName = "Art",
        )
    private val discover =
        PinnedFeedUi(
            id = "discover",
            uri = "at://did:plc:x/app.bsky.feed.generator/whats-hot",
            kind = FeedKind.Generator,
            displayName = "Discover",
        )
    private val friendsList =
        PinnedFeedUi(
            id = "friends",
            uri = "at://did:plc:x/app.bsky.graph.list/friends",
            kind = FeedKind.List,
            displayName = "Friends",
        )

    /**
     * Builds a VM with mocked repos. [restoreSelectedFeedUri] is stubbed to
     * mirror the real validation: return the persisted URI if it is in the
     * pinned set, else the Following sentinel.
     */
    private fun buildVm(
        feeds: List<PinnedFeedUi>,
        usedFallback: Boolean = false,
        persisted: String? = null,
    ): Triple<FeedHostViewModel, PinnedFeedsRepository, UserPreferencesRepository> {
        val pinnedRepo = mockk<PinnedFeedsRepository>()
        coEvery { pinnedRepo.loadPinnedFeeds() } returns PinnedFeedsResult(feeds, usedFallback)
        every { pinnedRepo.validateSelectedFeedUri(any(), any()) } answers {
            val p = firstArg<String?>()
            val pinned = secondArg<List<PinnedFeedUi>>()
            if (p != null && pinned.any { it.uri == p }) p else FOLLOWING_FEED_URI
        }
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { prefs.lastSelectedFeedUri } returns flowOf(persisted)
        val vm = FeedHostViewModel(pinnedRepo, prefs)
        return Triple(vm, pinnedRepo, prefs)
    }

    @Test
    fun `load success splits feeds and lists and reaches Ready`() =
        runTest {
            val (vm, _, _) = buildVm(feeds = listOf(following, art, friendsList))

            val state = vm.uiState.value
            assertIs<FeedHostStatus.Ready>(state.status)
            assertEquals(listOf(following, art), state.feedChips.toList())
            assertEquals(listOf(friendsList), state.pinnedLists.toList())
        }

    @Test
    fun `load fallback reaches ErrorFallback and emits ShowError but keeps chips`() =
        runTest {
            val (vm, _, _) =
                buildVm(feeds = listOf(following, discover), usedFallback = true)

            assertIs<FeedHostStatus.ErrorFallback>(vm.uiState.value.status)
            assertEquals(
                listOf(following, discover),
                vm.uiState.value.feedChips
                    .toList(),
            )

            vm.effects.test {
                assertIs<FeedHostEffect.ShowError>(awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `restore uses persisted uri when still pinned`() =
        runTest {
            val (vm, _, _) =
                buildVm(feeds = listOf(following, art), persisted = art.uri)

            assertEquals(art.uri, vm.uiState.value.selectedFeedUri)
        }

    @Test
    fun `restore falls back to Following when persisted uri is stale`() =
        runTest {
            val (vm, _, _) =
                buildVm(
                    feeds = listOf(following, art),
                    persisted = "at://did:plc:x/app.bsky.feed.generator/gone",
                )

            assertEquals(FOLLOWING_FEED_URI, vm.uiState.value.selectedFeedUri)
        }

    @Test
    fun `SelectFeed updates selection and persists it`() =
        runTest {
            val (vm, _, prefs) = buildVm(feeds = listOf(following, art))

            vm.handleEvent(FeedHostEvent.SelectFeed(art.uri))

            assertEquals(art.uri, vm.uiState.value.selectedFeedUri)
            coVerify { prefs.setLastSelectedFeedUri(art.uri) }
        }

    @Test
    fun `SelectList activates the list and persists it`() =
        runTest {
            val (vm, _, prefs) = buildVm(feeds = listOf(following, friendsList))

            vm.handleEvent(FeedHostEvent.SelectList(friendsList.uri))

            assertEquals(friendsList.uri, vm.uiState.value.selectedFeedUri)
            coVerify { prefs.setLastSelectedFeedUri(friendsList.uri) }
        }

    @Test
    fun `re-selecting the active feed does not re-persist`() =
        runTest {
            val (vm, _, prefs) = buildVm(feeds = listOf(following, art), persisted = art.uri)

            // art is already selected via restore; re-selecting is a no-op.
            vm.handleEvent(FeedHostEvent.SelectFeed(art.uri))

            coVerify(exactly = 0) { prefs.setLastSelectedFeedUri(any()) }
        }
}
