package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `init kicks off refreshConvos`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            ChatsViewModel(repository = repo)
            advanceUntilIdle()
            assertEquals(1, repo.refreshCalls.get())
        }

    @Test
    fun `success commits Loaded with the cached items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
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
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf()))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertTrue((status as ChatsLoadStatus.Loaded).items.isEmpty())
        }

    @Test
    fun `IOException maps to InitialError(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.failure(IOException("net down")))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.InitialError)
            assertEquals(ChatsError.Network, (status as ChatsLoadStatus.InitialError).error)
        }

    @Test
    fun `a cache update (e_g_ a thread send) refreshes the list live without a refetch`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"), sampleItem("c2"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val callsBefore = repo.refreshCalls.get()

            // Simulate sendMessage patching the shared cache: c2 hoisted, preview updated.
            repo.emitConvos(
                persistentListOf(
                    sampleItem("c2").copy(lastMessageSnippet = "just sent", lastMessageFromViewer = true),
                    sampleItem("c1"),
                ),
            )
            advanceUntilIdle()

            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            val items = (status as ChatsLoadStatus.Loaded).items
            assertEquals(listOf("c2", "c1"), items.map { it.convoId })
            assertEquals("just sent", items[0].lastMessageSnippet)
            assertTrue(items[0].lastMessageFromViewer)
            // No network refetch happened — the inbox reacted to the cache.
            assertEquals(callsBefore, repo.refreshCalls.get())
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
    fun `SettingsTapped emits NavigateToChatSettings`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.effects.test {
                vm.handleEvent(ChatsEvent.SettingsTapped)
                assertEquals(ChatsEffect.NavigateToChatSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `RetryClicked re-issues refreshConvos`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.failure(IOException("net down")))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val priorCalls = repo.refreshCalls.get()
            repo.nextRefreshResult = Result.success(persistentListOf(sampleItem("c1")))
            vm.handleEvent(ChatsEvent.RetryClicked)
            advanceUntilIdle()
            assertEquals(priorCalls + 1, repo.refreshCalls.get())
            assertTrue(vm.uiState.value.status is ChatsLoadStatus.Loaded)
        }

    @Test
    fun `Refresh on Loaded commits new items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            repo.nextRefreshResult = Result.success(persistentListOf(sampleItem("c2")))
            vm.handleEvent(ChatsEvent.Refresh)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertEquals("c2", (status as ChatsLoadStatus.Loaded).items[0].convoId)
        }

    @Test
    fun `double-Refresh is single-flighted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val priorCalls = repo.refreshCalls.get()
            vm.handleEvent(ChatsEvent.Refresh)
            vm.handleEvent(ChatsEvent.Refresh)
            advanceUntilIdle()
            // First Refresh issues a call; the second is dropped because the first is in flight.
            assertEquals(priorCalls + 1, repo.refreshCalls.get())
        }

    @Test
    fun `Refresh failure on Loaded keeps existing items + emits ShowRefreshError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            val priorStatus = vm.uiState.value.status
            assertTrue(priorStatus is ChatsLoadStatus.Loaded)
            assertEquals("c1", (priorStatus as ChatsLoadStatus.Loaded).items[0].convoId)

            // Flip the fake to fail; the cache (and so the items) stays put.
            repo.nextRefreshResult = Result.failure(IOException("net down"))
            vm.effects.test {
                vm.handleEvent(ChatsEvent.Refresh)
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is ChatsEffect.ShowRefreshError)
                assertEquals(ChatsError.Network, (effect as ChatsEffect.ShowRefreshError).error)
                cancelAndIgnoreRemainingEvents()
            }

            val finalStatus = vm.uiState.value.status
            assertTrue(finalStatus is ChatsLoadStatus.Loaded, "status should remain Loaded after a failed refresh")
            val loaded = finalStatus as ChatsLoadStatus.Loaded
            assertEquals(1, loaded.items.size)
            assertEquals("c1", loaded.items[0].convoId)
            assertEquals(false, loaded.isRefreshing)
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
            sentAt = Instant.parse("2026-05-13T11:50:00Z"),
        )
}
