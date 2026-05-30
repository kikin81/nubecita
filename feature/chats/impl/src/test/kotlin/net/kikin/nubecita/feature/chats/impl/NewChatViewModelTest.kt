package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.ActorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class NewChatViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repo = mockk<ActorRepository>(relaxed = true)
    private val session =
        mockk<SessionStateProvider> {
            every { state } returns MutableStateFlow(SessionState.SignedIn(handle = "me", did = "did:self"))
        }

    private fun vm() = NewChatViewModel(repo, session)

    private fun actor(did: String) = ActorUi(did, "$did.bsky", null, null)

    @Test
    fun `blankQuery_loadsRecentFromCache_selfExcludedViaRepo`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors("did:self", any()) } returns flowOf(listOf(actor("did:a")))

            val vm = vm()
            advanceUntilIdle()

            val status = vm.uiState.value.status
            assertTrue(status is NewChatStatus.Recent, "expected Recent, got $status")
            val recent = status as NewChatStatus.Recent
            assertEquals(listOf("did:a"), recent.items.map { it.did })
        }

    @Test
    fun `typingQuery_emitsResults_selfFiltered`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
            coEvery { repo.searchTypeahead("jay", any()) } returns
                Result.success(listOf(actor("did:self"), actor("did:jay")))

            val vm = vm()
            advanceUntilIdle()

            setQueryText(vm, "jay")
            testScheduler.advanceUntilIdle()

            val status = vm.uiState.value.status
            assertTrue(status is NewChatStatus.Results, "expected Results, got $status")
            val results = status as NewChatStatus.Results
            assertEquals(listOf("did:jay"), results.items.map { it.did }, "self must be filtered out")
        }

    @Test
    fun `clearingQuery_returnsToRecentImmediately`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flowOf(listOf(actor("did:a")))
            coEvery { repo.searchTypeahead(any(), any()) } returns Result.success(listOf(actor("did:b")))

            val vm = vm()
            advanceUntilIdle()

            // Type something to get into search mode.
            setQueryText(vm, "jay")
            testScheduler.advanceUntilIdle()

            // Now clear the query — blank path should bypass the 250ms delay.
            setQueryText(vm, "")
            testScheduler.runCurrent()

            val status = vm.uiState.value.status
            assertTrue(status is NewChatStatus.Recent, "expected Recent immediately after clearing, got $status")
        }

    @Test
    fun `searchFailure_thenRetry_reRunsSameQuery`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
            coEvery { repo.searchTypeahead("jay", any()) } returnsMany
                listOf(
                    Result.failure(IOException("net down")),
                    Result.success(listOf(actor("did:jay"))),
                )

            val vm = vm()
            advanceUntilIdle()

            setQueryText(vm, "jay")
            testScheduler.advanceUntilIdle()

            val errorStatus = vm.uiState.value.status
            assertTrue(errorStatus is NewChatStatus.Error, "expected Error after search failure, got $errorStatus")

            vm.handleEvent(NewChatEvent.RetryClicked)
            testScheduler.advanceUntilIdle()

            val retryStatus = vm.uiState.value.status
            assertTrue(retryStatus is NewChatStatus.Results, "expected Results after retry, got $retryStatus")

            coVerify(exactly = 2) { repo.searchTypeahead("jay", any()) }
        }

    @Test
    fun `emptySearchResults_emitNoResults`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
            coEvery { repo.searchTypeahead(any(), any()) } returns Result.success(emptyList())

            val vm = vm()
            advanceUntilIdle()

            setQueryText(vm, "nobody")
            testScheduler.advanceUntilIdle()

            val status = vm.uiState.value.status
            assertTrue(status is NewChatStatus.NoResults, "expected NoResults, got $status")
        }

    @Test
    fun `recipientSelected_emitsOpenChatEffect`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flowOf(emptyList())

            val vm = vm()
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NewChatEvent.RecipientSelected("did:jay"))

                val effect = awaitItem()
                assertEquals(NewChatEffect.OpenChat("did:jay"), effect)
            }
        }

    @Test
    fun `typingQuery_emitsSearchingBeforeResults`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
            coEvery { repo.searchTypeahead(any(), any()) } returns Result.success(listOf(actor("did:jay")))

            val vm = vm()
            advanceUntilIdle()

            // Set query but do NOT advance past the 250ms debounce.
            setQueryText(vm, "jay")
            // runCurrent drives the snapshot collector and the flow up to its first suspension
            // (the delay). The Searching status must already be set.
            val status = vm.uiState.value.status
            assertTrue(status is NewChatStatus.Searching, "expected Searching before debounce, got $status")
        }

    @Test
    fun `recentCacheError_surfacesErrorAndPipelineSurvives`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flow { throw RuntimeException("db") }
            coEvery { repo.searchTypeahead(any(), any()) } returns Result.success(listOf(actor("did:jay")))

            val vm = vm()
            advanceUntilIdle()

            val errorStatus = vm.uiState.value.status
            assertTrue(errorStatus is NewChatStatus.Error, "expected Error from cache failure, got $errorStatus")

            // Now type a query — the outer pipeline must still be alive.
            setQueryText(vm, "jay")
            testScheduler.advanceUntilIdle()

            val resultsStatus = vm.uiState.value.status
            assertTrue(
                resultsStatus is NewChatStatus.Results,
                "expected Results after pipeline survived cache error, got $resultsStatus",
            )
            assertEquals(listOf("did:jay"), (resultsStatus as NewChatStatus.Results).items.map { it.did })
        }

    /**
     * Mutates the VM's [NewChatViewModel.queryFieldState] and drives the Compose
     * snapshot system so the change reaches the VM's `snapshotFlow` collector.
     * No frame loop exists in unit tests, so [Snapshot.sendApplyNotifications]
     * is triggered manually; [runCurrent] then advances past the collector's
     * suspending boundary. Mirrors `ChatViewModelTest.setComposerText`.
     */
    private fun TestScope.setQueryText(
        vm: NewChatViewModel,
        text: String,
    ) {
        vm.queryFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
    }
}
