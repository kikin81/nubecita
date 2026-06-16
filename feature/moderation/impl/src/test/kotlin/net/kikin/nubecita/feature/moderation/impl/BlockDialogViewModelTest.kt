package net.kikin.nubecita.feature.moderation.impl

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.actors.BlockRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.moderation.api.Block
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class BlockDialogViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val route = Block(did = "did:plc:alice", handle = "alice.bsky.social")

    @Test
    fun `confirm success blocks then requests dismiss`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBlockRepository(result = Result.success(Unit))
            val vm = BlockDialogViewModel(route = route, blockRepository = repo)
            vm.effects.test {
                vm.handleEvent(BlockDialogEvent.OnConfirmClicked)
                advanceUntilIdle()
                assertEquals(BlockDialogEffect.RequestDismiss, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("did:plc:alice"), repo.blockedDids)
        }

    @Test
    fun `confirm failure shows retryable error and does not dismiss`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBlockRepository(result = Result.failure(RuntimeException("network")))
            val vm = BlockDialogViewModel(route = route, blockRepository = repo)
            vm.effects.test {
                vm.handleEvent(BlockDialogEvent.OnConfirmClicked)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            val state = vm.uiState.value
            assertTrue(state.hasError)
            assertFalse(state.isSubmitting)
        }

    @Test
    fun `cancel requests dismiss without blocking`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBlockRepository(result = Result.success(Unit))
            val vm = BlockDialogViewModel(route = route, blockRepository = repo)
            vm.effects.test {
                vm.handleEvent(BlockDialogEvent.OnCancelClicked)
                assertEquals(BlockDialogEffect.RequestDismiss, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(repo.blockedDids.isEmpty())
        }

    @Test
    fun `double confirm is single-flighted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBlockRepository(result = Result.success(Unit))
            val vm = BlockDialogViewModel(route = route, blockRepository = repo)
            vm.handleEvent(BlockDialogEvent.OnConfirmClicked)
            vm.handleEvent(BlockDialogEvent.OnConfirmClicked)
            advanceUntilIdle()
            assertEquals(1, repo.blockedDids.size)
        }

    private class FakeBlockRepository(
        private val result: Result<Unit>,
    ) : BlockRepository {
        val blockedDids = mutableListOf<String>()

        override suspend fun blockActor(did: String): Result<Unit> {
            blockedDids += did
            return result
        }

        override suspend fun blockedAccounts(): Result<List<net.kikin.nubecita.data.models.BlockedAccount>> = Result.success(emptyList())

        override suspend fun unblockActor(blockUri: String): Result<Unit> = Result.success(Unit)
    }
}
