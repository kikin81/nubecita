package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.moderation.api.Block
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.profile.api.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `init refreshes both accepted and request convos`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            ChatsViewModel(repository = repo)
            advanceUntilIdle()
            assertEquals(1, repo.refreshCalls.get())
            assertEquals(1, repo.refreshRequestCalls.get())
        }

    @Test
    fun `default segment is Chats and shows accepted items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))),
                    nextRequestRefreshResult = Result.success(persistentListOf(sampleItem("r1"))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            assertEquals(ChatsSegment.Chats, vm.uiState.value.activeSegment)
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertEquals(listOf("c1"), (status as ChatsLoadStatus.Loaded).items.map { it.convoId })
        }

    @Test
    fun `requestCount reflects pending requests for the badge`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))),
                    nextRequestRefreshResult = Result.success(persistentListOf(sampleItem("r1"), sampleItem("r2"))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            // Count is exposed while on the Chats segment (drives the Requests pill badge).
            assertEquals(2, vm.uiState.value.requestCount)
        }

    @Test
    fun `SegmentSelected Requests projects the request list`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))),
                    nextRequestRefreshResult = Result.success(persistentListOf(sampleItem("r1"))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.SegmentSelected(ChatsSegment.Requests))
            advanceUntilIdle()
            assertEquals(ChatsSegment.Requests, vm.uiState.value.activeSegment)
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.Loaded)
            assertEquals(listOf("r1"), (status as ChatsLoadStatus.Loaded).items.map { it.convoId })
        }

    @Test
    fun `requests failure degrades to Requests-only error while Chats stays Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))),
                    nextRequestRefreshResult = Result.failure(IOException("requests down")),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            // Chats segment is unaffected.
            assertTrue(vm.uiState.value.status is ChatsLoadStatus.Loaded)

            // Switching to Requests surfaces the requests-only error inline.
            vm.handleEvent(ChatsEvent.SegmentSelected(ChatsSegment.Requests))
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatsLoadStatus.InitialError)
            assertEquals(ChatsError.Network, (status as ChatsLoadStatus.InitialError).error)
        }

    @Test
    fun `requests failure does not emit a snackbar effect`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))),
                    nextRequestRefreshResult = Result.failure(IOException("requests down")),
                )
            val vm = ChatsViewModel(repository = repo)
            vm.effects.test {
                advanceUntilIdle()
                // Accepted succeeded and requests fail inline — no transient snackbar.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- Multi-select contextual actions (kc17.3) ----

    @Test
    fun `ConvoLongPressed enters selection mode with that convo`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"), sampleItem("c2"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            advanceUntilIdle()
            assertEquals(setOf("c1"), vm.uiState.value.selection)
        }

    @Test
    fun `SelectionToggled adds then removes, and emptying exits selection`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"), sampleItem("c2"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.handleEvent(ChatsEvent.SelectionToggled("c2"))
            advanceUntilIdle()
            assertEquals(setOf("c1", "c2"), vm.uiState.value.selection)
            vm.handleEvent(ChatsEvent.SelectionToggled("c1"))
            advanceUntilIdle()
            assertEquals(setOf("c2"), vm.uiState.value.selection)
            // Toggling the last selected convo off drops out of selection mode.
            vm.handleEvent(ChatsEvent.SelectionToggled("c2"))
            advanceUntilIdle()
            assertNull(vm.uiState.value.selection)
        }

    @Test
    fun `ClearSelection exits selection mode`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.handleEvent(ChatsEvent.ClearSelection)
            advanceUntilIdle()
            assertNull(vm.uiState.value.selection)
        }

    @Test
    fun `SegmentSelected clears any in-progress selection`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.handleEvent(ChatsEvent.SegmentSelected(ChatsSegment.Requests))
            advanceUntilIdle()
            assertNull(vm.uiState.value.selection)
        }

    @Test
    fun `LeaveSelected leaves every selected convo then exits selection`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"), sampleItem("c2"), sampleItem("c3"))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.handleEvent(ChatsEvent.SelectionToggled("c2"))
            vm.handleEvent(ChatsEvent.LeaveSelected)
            advanceUntilIdle()
            assertEquals(setOf("c1", "c2"), repo.leaveCalls.toSet())
            assertNull(vm.uiState.value.selection)
        }

    @Test
    fun `LeaveSelected surfaces the first failure as ShowActionError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            repo.nextLeaveResult = Result.failure(IOException("leave down"))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.effects.test {
                vm.handleEvent(ChatsEvent.LeaveSelected)
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is ChatsEffect.ShowActionError)
                assertEquals(ChatsError.Network, (effect as ChatsEffect.ShowActionError).error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `AcceptSelected accepts every selected request`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))),
                    nextRequestRefreshResult = Result.success(persistentListOf(sampleItem("r1"), sampleItem("r2"))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.SegmentSelected(ChatsSegment.Requests))
            vm.handleEvent(ChatsEvent.ConvoLongPressed("r1"))
            vm.handleEvent(ChatsEvent.SelectionToggled("r2"))
            vm.handleEvent(ChatsEvent.AcceptSelected)
            advanceUntilIdle()
            assertEquals(setOf("r1", "r2"), repo.acceptCalls.toSet())
            assertNull(vm.uiState.value.selection)
        }

    @Test
    fun `ToggleMuteSelected mutes when any selected convo is unmuted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult =
                        Result.success(
                            persistentListOf(sampleItem("c1").copy(muted = true), sampleItem("c2").copy(muted = false)),
                        ),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.handleEvent(ChatsEvent.SelectionToggled("c2"))
            vm.handleEvent(ChatsEvent.ToggleMuteSelected)
            advanceUntilIdle()
            // c2 is unmuted, so the toggle target is "mute all".
            assertEquals(setOf("c1" to true, "c2" to true), repo.setMutedCalls.toSet())
        }

    @Test
    fun `ToggleMuteSelected unmutes when every selected convo is muted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextRefreshResult = Result.success(persistentListOf(sampleItem("c1").copy(muted = true))),
                )
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.handleEvent(ChatsEvent.ToggleMuteSelected)
            advanceUntilIdle()
            assertEquals(listOf("c1" to false), repo.setMutedCalls)
        }

    @Test
    fun `ProfileSelected at a single selection navigates to that profile`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.effects.test {
                vm.handleEvent(ChatsEvent.ProfileSelected)
                assertEquals(ChatsEffect.NavigateTo(Profile(handle = "alice.bsky.social")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            // navigateSingle exits selection after dispatching.
            assertNull(vm.uiState.value.selection)
        }

    @Test
    fun `ReportSelected navigates to Report for the account DID`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.effects.test {
                vm.handleEvent(ChatsEvent.ReportSelected)
                assertEquals(ChatsEffect.NavigateTo(Report.forAccount("did:plc:alice")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BlockSelected navigates to Block for the account DID + handle`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.effects.test {
                vm.handleEvent(ChatsEvent.BlockSelected)
                assertEquals(
                    ChatsEffect.NavigateTo(Block.forAccount(did = "did:plc:alice", handle = "alice.bsky.social")),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a single-target action no-ops while multiple are selected`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextRefreshResult = Result.success(persistentListOf(sampleItem("c1"), sampleItem("c2"))))
            val vm = ChatsViewModel(repository = repo)
            advanceUntilIdle()
            vm.handleEvent(ChatsEvent.ConvoLongPressed("c1"))
            vm.handleEvent(ChatsEvent.SelectionToggled("c2"))
            vm.effects.test {
                vm.handleEvent(ChatsEvent.ProfileSelected)
                advanceUntilIdle()
                // navigateSingle requires exactly one selection — no effect, selection intact.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(setOf("c1", "c2"), vm.uiState.value.selection)
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
