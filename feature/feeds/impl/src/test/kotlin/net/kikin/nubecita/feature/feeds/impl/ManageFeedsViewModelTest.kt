package net.kikin.nubecita.feature.feeds.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class ManageFeedsViewModelTest {
    // Unconfined so the observePinnedFeeds collector and application-scoped commits
    // run eagerly on emit — this is an event-driven VM with no delays to advance.
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension(UnconfinedTestDispatcher())

    private val repository = mockk<PinnedFeedsRepository>(relaxed = true)
    private val pinnedFlow = MutableSharedFlow<PinnedFeedsResult>(replay = 1)

    @BeforeEach
    fun setup() {
        every { repository.observePinnedFeeds() } returns pinnedFlow
        coEvery { repository.refresh() } returns Result.success(Unit)
    }

    private fun TestScope.buildVm(): ManageFeedsViewModel =
        ManageFeedsViewModel(
            repository = repository,
            applicationScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

    private fun feed(
        uri: String,
        kind: FeedKind = FeedKind.Generator,
        name: String = uri,
    ): PinnedFeedUi = PinnedFeedUi(id = uri, uri = uri, kind = kind, displayName = name, avatarUrl = null)

    private val following = feed("following", FeedKind.Following, "Following")
    private val a = feed("at://a")
    private val b = feed("at://b")

    private fun emit(vararg feeds: PinnedFeedUi) = PinnedFeedsResult(feeds.toList().toImmutableList(), usedFallback = false)

    private fun ManageFeedsViewModel.orderUris(): List<String> = (uiState.value.status as ManageFeedsLoadStatus.Content).feeds.map { it.uri }

    @Test
    fun `seeds pinned feeds into Content on first emission`() =
        runTest {
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))

            assertInstanceOf(ManageFeedsLoadStatus.Content::class.java, vm.uiState.value.status)
            assertEquals(listOf("following", "at://a", "at://b"), vm.orderUris())
        }

    @Test
    fun `Move reorders the feeds in state`() =
        runTest {
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))

            vm.handleEvent(ManageFeedsEvent.Move(1, 2))

            assertEquals(listOf("following", "at://b", "at://a"), vm.orderUris())
        }

    @Test
    fun `Remove optimistically drops the row and calls unpinFeed`() =
        runTest {
            coEvery { repository.unpinFeed("at://a") } returns Result.success(Unit)
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))

            vm.handleEvent(ManageFeedsEvent.Remove("at://a"))

            assertEquals(listOf("following", "at://b"), vm.orderUris())
            coVerify { repository.unpinFeed("at://a") }
        }

    @Test
    fun `Remove failure restores the row and emits ShowRemoveError`() =
        runTest {
            coEvery { repository.unpinFeed("at://a") } returns Result.failure(IOException("network"))
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))

            vm.effects.test {
                vm.handleEvent(ManageFeedsEvent.Remove("at://a"))
                assertEquals(ManageFeedsEffect.ShowRemoveError, awaitItem())
            }
            // Row restored to its original index.
            assertEquals(listOf("following", "at://a", "at://b"), vm.orderUris())
        }

    @Test
    fun `commitReorderIfDirty commits the new order when the order changed`() =
        runTest {
            coEvery { repository.reorderPinnedFeeds(any()) } returns Result.success(Unit)
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))
            vm.handleEvent(ManageFeedsEvent.Move(1, 2))

            vm.commitReorderIfDirty()

            coVerify(exactly = 1) { repository.reorderPinnedFeeds(listOf("following", "at://b", "at://a")) }
        }

    @Test
    fun `commitReorderIfDirty does not commit when the order is unchanged`() =
        runTest {
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))

            vm.commitReorderIfDirty()

            coVerify(exactly = 0) { repository.reorderPinnedFeeds(any()) }
        }

    @Test
    fun `commitReorderIfDirty commits once, not again for the same order`() =
        runTest {
            coEvery { repository.reorderPinnedFeeds(any()) } returns Result.success(Unit)
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))
            vm.handleEvent(ManageFeedsEvent.Move(1, 2))

            vm.commitReorderIfDirty()
            vm.commitReorderIfDirty()

            coVerify(exactly = 1) { repository.reorderPinnedFeeds(any()) }
        }

    @Test
    fun `a remove alone is not a dirty reorder — no commit on exit`() =
        runTest {
            coEvery { repository.unpinFeed("at://a") } returns Result.success(Unit)
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))

            vm.handleEvent(ManageFeedsEvent.Remove("at://a"))
            vm.commitReorderIfDirty()

            coVerify(exactly = 0) { repository.reorderPinnedFeeds(any()) }
        }

    @Test
    fun `ignores upstream re-emission after a local reorder`() =
        runTest {
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))
            vm.handleEvent(ManageFeedsEvent.Move(1, 2)) // now [following, b, a] (dirty)

            pinnedFlow.emit(emit(following, a, b)) // background refresh — must NOT stomp the drag

            assertEquals(listOf("following", "at://b", "at://a"), vm.orderUris())
        }

    @Test
    fun `upstream re-emission during a pending remove does not re-add the feed`() =
        runTest {
            // unpinFeed stays in flight, so the optimistic remove is still pending when the
            // upstream emission (which still contains the feed) arrives.
            val gate = CompletableDeferred<Result<Unit>>()
            coEvery { repository.unpinFeed("at://a") } coAnswers { gate.await() }
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))

            vm.handleEvent(ManageFeedsEvent.Remove("at://a")) // local [following, b], unpin in flight

            pinnedFlow.emit(emit(following, a, b)) // Room not yet updated — feed still present upstream

            // The removed feed must not flicker back in.
            assertEquals(listOf("following", "at://b"), vm.orderUris())
        }

    @Test
    fun `a failed reorder commit self-heals via the rollback emission and does not get stuck dirty`() =
        runTest {
            coEvery { repository.reorderPinnedFeeds(any()) } returns Result.failure(IOException("network"))
            val vm = buildVm()
            pinnedFlow.emit(emit(following, a, b))
            vm.handleEvent(ManageFeedsEvent.Move(1, 2)) // local [following, b, a]

            vm.commitReorderIfDirty() // fails; repo rolls back Room to the old order

            // The rollback emission re-seeds the list + seededOrder to the server's order.
            pinnedFlow.emit(emit(following, a, b))
            assertEquals(listOf("following", "at://a", "at://b"), vm.orderUris())

            // Not stuck dirty: a second commit is a no-op (only the one failed attempt happened).
            vm.commitReorderIfDirty()
            coVerify(exactly = 1) { repository.reorderPinnedFeeds(any()) }
        }
}
