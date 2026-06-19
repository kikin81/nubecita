package net.kikin.nubecita.feature.moderation.impl

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
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
                    unblock = { Result.failure(RuntimeException("net")) },
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

    @Test
    fun `a late unblock failure does not resurrect a concurrently-unblocked account`() =
        runTest(mainDispatcher.dispatcher) {
            val a = account("a")
            val b = account("b")
            val gateA = CompletableDeferred<Unit>()
            val repo =
                FakeBlockRepository(
                    accounts = Result.success(listOf(a, b)),
                    unblock = { uri -> if (uri == a.blockUri) Result.failure(RuntimeException("net")) else Result.success(Unit) },
                    gates = mapOf(a.blockUri to gateA),
                )
            val vm = BlockedAccountsViewModel(repo)
            advanceUntilIdle()

            // Unblock A (its call is gated → stays in flight) then B (succeeds now).
            vm.handleEvent(BlockedAccountsEvent.UnblockClicked(a))
            vm.handleEvent(BlockedAccountsEvent.UnblockClicked(b))
            advanceUntilIdle()
            // Both optimistically removed; A's unblock is still gated.
            assertTrue((vm.uiState.value.status as BlockedAccountsStatus.Loaded).accounts.isEmpty())

            // Let A's gated unblock complete → it fails → A is restored, B stays gone.
            gateA.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("did:a"), (vm.uiState.value.status as BlockedAccountsStatus.Loaded).accounts.map { it.did })
        }

    private fun account(id: String): BlockedAccount =
        BlockedAccount(
            did = "did:$id",
            handle = "$id.bsky.social",
            displayName = id.uppercase(),
            avatarUrl = null,
            blockUri = "at://did:$id/app.bsky.graph.block/$id",
        )

    private class FakeBlockRepository(
        private val accounts: Result<List<BlockedAccount>>,
        // Per-block-URI result; defaults to success. A function so a test can fail
        // a specific account's unblock.
        private val unblock: (String) -> Result<Unit> = { Result.success(Unit) },
        // Per-block-URI gate: when present, unblockActor awaits it before returning,
        // letting a test control completion ordering across concurrent unblocks.
        private val gates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
    ) : BlockRepository {
        val unblockedUris = mutableListOf<String>()

        override suspend fun blockActor(did: String): Result<Unit> = Result.success(Unit)

        override suspend fun blockedAccounts(): Result<List<BlockedAccount>> = accounts

        override suspend fun unblockActor(blockUri: String): Result<Unit> {
            gates[blockUri]?.await()
            unblockedUris += blockUri
            return unblock(blockUri)
        }
    }
}
