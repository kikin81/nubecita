package net.kikin.nubecita.feature.moderation.impl

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.actors.BlockRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.BlockedAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class BlockedAccountsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `init loads blocked accounts`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBlockRepository(accounts = Result.success(listOf(account("a"), account("b"))))
            val vm = BlockedAccountsViewModel(repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is BlockedAccountsStatus.Loaded)
            assertEquals(listOf("did:a", "did:b"), (status as BlockedAccountsStatus.Loaded).accounts.map { it.did })
        }

    @Test
    fun `load failure yields Error`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBlockRepository(accounts = Result.failure(RuntimeException("net")))
            val vm = BlockedAccountsViewModel(repo)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.status is BlockedAccountsStatus.Error)
        }

    @Test
    fun `unblock optimistically removes the row`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBlockRepository(accounts = Result.success(listOf(account("a"), account("b"))))
            val vm = BlockedAccountsViewModel(repo)
            advanceUntilIdle()
            vm.handleEvent(BlockedAccountsEvent.UnblockClicked(account("a")))
            advanceUntilIdle()
            val status = vm.uiState.value.status as BlockedAccountsStatus.Loaded
            assertEquals(listOf("did:b"), status.accounts.map { it.did })
            assertEquals(listOf("at://did:a/app.bsky.graph.block/a"), repo.unblockedUris)
        }

    @Test
    fun `unblock failure restores the row and emits error`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeBlockRepository(
                    accounts = Result.success(listOf(account("a"), account("b"))),
                    unblock = Result.failure(RuntimeException("net")),
                )
            val vm = BlockedAccountsViewModel(repo)
            advanceUntilIdle()
            vm.effects.test {
                vm.handleEvent(BlockedAccountsEvent.UnblockClicked(account("a")))
                advanceUntilIdle()
                assertEquals(BlockedAccountsEffect.ShowUnblockError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            val status = vm.uiState.value.status as BlockedAccountsStatus.Loaded
            assertEquals(listOf("did:a", "did:b"), status.accounts.map { it.did })
        }

    private fun account(id: String): BlockedAccount =
        BlockedAccount(
            did = "did:$id",
            handle = "$id.bsky.social",
            displayName = id.uppercase(),
            avatarUrl = null,
            avatarHue = 200,
            blockUri = "at://did:$id/app.bsky.graph.block/$id",
        )

    private class FakeBlockRepository(
        private val accounts: Result<List<BlockedAccount>>,
        private val unblock: Result<Unit> = Result.success(Unit),
    ) : BlockRepository {
        val unblockedUris = mutableListOf<String>()

        override suspend fun blockActor(did: String): Result<Unit> = Result.success(Unit)

        override suspend fun blockedAccounts(): Result<List<BlockedAccount>> = accounts

        override suspend fun unblockActor(blockUri: String): Result<Unit> {
            unblockedUris += blockUri
            return unblock
        }
    }
}
