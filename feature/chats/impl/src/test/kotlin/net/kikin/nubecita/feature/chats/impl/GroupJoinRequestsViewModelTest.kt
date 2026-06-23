package net.kikin.nubecita.feature.chats.impl

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.api.GroupJoinRequests
import net.kikin.nubecita.feature.chats.impl.data.JoinRequestPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class GroupJoinRequestsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val convoId = "c1"

    private fun joinRequest(did: String) =
        JoinRequestUi(
            did = did,
            handle = "$did.bsky.social",
            displayName = null,
            avatarUrl = null,
            requestedAt = Instant.parse("2026-06-22T10:00:00Z"),
        )

    private fun vm(repo: FakeChatRepository): GroupJoinRequestsViewModel = GroupJoinRequestsViewModel(route = GroupJoinRequests(convoId), repository = repo)

    @Test
    fun `approve removes row, records call, emits RosterChanged`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getJoinRequestsResult =
                Result.success(JoinRequestPage(persistentListOf(joinRequest("did:a"), joinRequest("did:b"))))
            val vm = vm(repo)

            assertEquals(listOf("did:a", "did:b"), vm.joinRequests.asSnapshot().map { it.did })

            vm.effects.test {
                vm.handleEvent(GroupJoinRequestsEvent.ApproveTapped("did:a"))
                advanceUntilIdle()
                assertEquals(GroupJoinRequestsEffect.RosterChanged, awaitItem())
            }

            assertEquals(listOf("did:b"), vm.joinRequests.asSnapshot().map { it.did })
            assertEquals(convoId to "did:a", repo.approveJoinRequestCalls.last())
        }

    @Test
    fun `reject removes row but emits no RosterChanged`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getJoinRequestsResult =
                Result.success(JoinRequestPage(persistentListOf(joinRequest("did:a"), joinRequest("did:b"))))
            val vm = vm(repo)

            vm.effects.test {
                vm.handleEvent(GroupJoinRequestsEvent.RejectTapped("did:a"))
                advanceUntilIdle()
                expectNoEvents()
            }

            assertEquals(listOf("did:b"), vm.joinRequests.asSnapshot().map { it.did })
            assertEquals(convoId to "did:a", repo.rejectJoinRequestCalls.last())
        }

    @Test
    fun `approve failure re-adds row and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getJoinRequestsResult =
                Result.success(JoinRequestPage(persistentListOf(joinRequest("did:a"), joinRequest("did:b"))))
            repo.approveJoinRequestResult =
                Result.failure(XrpcError(errorName = "InsufficientRole", errorMessage = "", status = 403))
            val vm = vm(repo)

            vm.effects.test {
                vm.handleEvent(GroupJoinRequestsEvent.ApproveTapped("did:a"))
                advanceUntilIdle()
                assertEquals(GroupJoinRequestsEffect.ShowError(ChatError.InsufficientPermission), awaitItem())
            }

            assertEquals(listOf("did:a", "did:b"), vm.joinRequests.asSnapshot().map { it.did })
            assertTrue(
                vm.uiState.value.inFlightDids
                    .isEmpty(),
            )
        }

    @Test
    fun `in-flight guard drops a second tap`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getJoinRequestsResult =
                Result.success(JoinRequestPage(persistentListOf(joinRequest("did:a"))))
            repo.approveJoinRequestGate = CompletableDeferred()
            val vm = vm(repo)

            vm.handleEvent(GroupJoinRequestsEvent.ApproveTapped("did:a"))
            vm.handleEvent(GroupJoinRequestsEvent.ApproveTapped("did:a"))
            advanceUntilIdle()

            assertEquals(1, repo.approveJoinRequestCalls.count { it.second == "did:a" })

            repo.approveJoinRequestGate!!.complete(Unit)
            advanceUntilIdle()
        }
}
