package net.kikin.nubecita.feature.settings.impl

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class WatchSettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val following = pinned(PinnedFeedsRepository.FOLLOWING_FEED_URI, FeedKind.Following, "Following")
    private val discover = pinned("at://feeds/discover", FeedKind.Generator, "Discover")
    private val cats = pinned("at://feeds/cats", FeedKind.Generator, "Cats")
    private val art = pinned("at://feeds/art", FeedKind.Generator, "Art")

    private val pinnedFlow = MutableStateFlow(listOf(following, discover, cats, art))
    private val preference = FakeWatchFeedsPreference()

    private fun vm(): WatchSettingsViewModel {
        val repository =
            mockk<PinnedFeedsRepository> {
                every { observePinnedFeeds() } returns
                    pinnedFlow.map { PinnedFeedsResult(it.toImmutableList(), usedFallback = false) }
            }
        return WatchSettingsViewModel(repository, preference)
    }

    @Test
    fun `following is the shared pseudo-URI in both modules`() {
        assertEquals(PinnedFeedsRepository.FOLLOWING_FEED_URI, WatchFeedsPreference.FOLLOWING_FEED_URI)
    }

    @Test
    fun `defaults to Following selected over the pinned feeds`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            advanceUntilIdle()

            assertEquals(
                listOf("Following", "Discover", "Cats", "Art"),
                vm.uiState.value.feeds
                    .map { it.displayName },
            )
            assertEquals(setOf(PinnedFeedsRepository.FOLLOWING_FEED_URI), vm.uiState.value.selectedUris)
        }

    @Test
    fun `checking a feed adds it to the selection`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            advanceUntilIdle()

            vm.handleEvent(WatchSettingsEvent.FeedToggled(cats.uri, selected = true))
            advanceUntilIdle()

            assertEquals(setOf(following.uri, cats.uri), preference.selected.value)
        }

    @Test
    fun `a fourth feed cannot be added`() =
        runTest(mainDispatcher.dispatcher) {
            preference.selected.value = setOf(following.uri, discover.uri, cats.uri)
            val vm = vm()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.canSelectMore)
            assertFalse(vm.uiState.value.isToggleable(art.uri))

            vm.handleEvent(WatchSettingsEvent.FeedToggled(art.uri, selected = true))
            advanceUntilIdle()

            assertEquals(setOf(following.uri, discover.uri, cats.uri), preference.selected.value)
            assertEquals(0, preference.writes)
        }

    @Test
    fun `at the cap, checked feeds can still be unchecked`() =
        runTest(mainDispatcher.dispatcher) {
            preference.selected.value = setOf(following.uri, discover.uri, cats.uri)
            val vm = vm()
            advanceUntilIdle()

            vm.handleEvent(WatchSettingsEvent.FeedToggled(discover.uri, selected = false))
            advanceUntilIdle()

            assertEquals(setOf(following.uri, cats.uri), preference.selected.value)
        }

    @Test
    fun `the last checked feed cannot be unchecked`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isToggleable(following.uri))

            vm.handleEvent(WatchSettingsEvent.FeedToggled(following.uri, selected = false))
            advanceUntilIdle()

            assertEquals(0, preference.writes)
        }

    @Test
    fun `Following stays choosable when the user unpinned it`() =
        runTest(mainDispatcher.dispatcher) {
            pinnedFlow.value = listOf(discover, cats)
            val vm = vm()
            advanceUntilIdle()

            assertEquals(
                PinnedFeedsRepository.FOLLOWING_FEED_URI,
                vm.uiState.value.feeds
                    .first()
                    .uri,
            )
            assertEquals(
                FeedKind.Following,
                vm.uiState.value.feeds
                    .first()
                    .kind,
            )
            assertTrue(PinnedFeedsRepository.FOLLOWING_FEED_URI in vm.uiState.value.selectedUris)
        }

    @Test
    fun `a selected feed that was unpinned no longer takes a slot`() =
        runTest(mainDispatcher.dispatcher) {
            preference.selected.value = setOf(following.uri, discover.uri, cats.uri)
            pinnedFlow.value = listOf(following, discover)
            val vm = vm()
            advanceUntilIdle()

            assertEquals(setOf(following.uri, discover.uri), vm.uiState.value.selectedUris)
            assertTrue(vm.uiState.value.canSelectMore)
        }

    @Test
    fun `a failed write shows the save error`() =
        runTest(mainDispatcher.dispatcher) {
            preference.failWrites = true
            val vm = vm()
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(WatchSettingsEvent.FeedToggled(cats.uri, selected = true))
                advanceUntilIdle()
                assertEquals(WatchSettingsEffect.ShowSaveError, awaitItem())
            }
        }

    private class FakeWatchFeedsPreference : WatchFeedsPreference {
        val selected = MutableStateFlow(setOf(WatchFeedsPreference.FOLLOWING_FEED_URI))
        var writes = 0
        var failWrites = false

        override val selectedFeedUris = selected

        override suspend fun setSelectedFeedUris(uris: Set<String>) {
            if (failWrites) error("disk full")
            writes++
            selected.value = uris
        }
    }

    private companion object {
        fun pinned(
            uri: String,
            kind: FeedKind,
            name: String,
        ) = PinnedFeedUi(id = uri, uri = uri, kind = kind, displayName = name, avatarUrl = null)
    }
}
