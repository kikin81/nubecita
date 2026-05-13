package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.impl.data.ConvoListPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `init kicks off listConvos`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            ChatsViewModel(repository = repo)
            advanceUntilIdle()
            assertEquals(1, repo.listCalls.get())
        }

    @Test
    fun `success commits Loaded with the wire items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1")))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertEquals(1, (status as ChatsLoadStatus.Loaded).items.size)
            assertEquals("c1", status.items[0].convoId)
        }

    @Test
    fun `empty result yields Loaded with no items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.success(ConvoListPage(items = persistentListOf())))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertTrue((status as ChatsLoadStatus.Loaded).items.isEmpty())
        }

    @Test
    fun `IOException maps to InitialError(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.failure(IOException("net down")))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.InitialError)
            assertEquals(ChatsError.Network, (status as ChatsLoadStatus.InitialError).error)
        }

    @Test
    fun `ConvoTapped emits NavigateToChat with the same DID`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.effects.test {
                vm.handleEvent(ChatsEvent.ConvoTapped(otherUserDid = "did:plc:alice"))
                val effect = awaitItem()
                assertEquals(ChatsEffect.NavigateToChat("did:plc:alice"), effect)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RetryClicked re-issues listConvos`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.failure(IOException("net down")))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val priorCalls = repo.listCalls.get()
            repo.nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1"))))
            vm.handleEvent(ChatsEvent.RetryClicked)
            advanceUntilIdle()
            assertEquals(priorCalls + 1, repo.listCalls.get())
            assertTrue(vm.uiState.value.status is ChatsLoadStatus.Loaded)
        }

    @Test
    fun `Refresh on Loaded sets isRefreshing then commits new items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1")))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            repo.nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c2"))))
            vm.handleEvent(ChatsEvent.Refresh)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertEquals("c2", (status as ChatsLoadStatus.Loaded).items[0].convoId)
        }

    @Test
    fun `double-Refresh is single-flighted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResult = Result.success(ConvoListPage(items = persistentListOf(sampleItem(convoId = "c1")))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val priorCalls = repo.listCalls.get()
            vm.handleEvent(ChatsEvent.Refresh)
            vm.handleEvent(ChatsEvent.Refresh)
            advanceUntilIdle()
            // First Refresh issues a call; the second is dropped because the first is in flight.
            assertEquals(priorCalls + 1, repo.listCalls.get())
        }

    private fun sampleItem(convoId: String): ConvoListItemUi =
        ConvoListItemUi(
            convoId = convoId,
            otherUserDid = "did:plc:alice",
            otherUserHandle = "alice.bsky.social",
            displayName = "Alice",
            avatarUrl = null,
            avatarHue = 217,
            lastMessageSnippet = "hello",
            lastMessageFromViewer = false,
            lastMessageIsAttachment = false,
            timestampRelative = "10m",
        )
}
