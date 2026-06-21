package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.chats.api.AddGroupMembers
import net.kikin.nubecita.feature.chats.impl.data.MemberPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class AddGroupMembersViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val actorRepo = mockk<ActorRepository>(relaxed = true)
    private val fakeChat = FakeChatRepository()
    private val session =
        mockk<SessionStateProvider> {
            every { state } returns MutableStateFlow(SessionState.SignedIn(handle = "me", did = "did:self"))
        }

    private val convoId = "convo-1"

    private fun vm() = AddGroupMembersViewModel(AddGroupMembers(convoId), actorRepo, fakeChat, session)

    private fun actorUi(did: String) = ActorUi(did, "$did.bsky", null, null, canMessage = true)

    private fun groupMember(did: String) =
        GroupMemberUi(
            did = did,
            handle = "$did.bsky",
            displayName = null,
            avatarUrl = null,
            role = GroupRole.Member,
            addedByName = null,
            isViewer = false,
            followState = FollowState.NotFollowing,
            followUri = null,
        )

    @Test
    fun `blankQuery_recentExcludesSelfAndExistingMembers`() =
        runTest(mainDispatcher.dispatcher) {
            fakeChat.getConvoMembersResult =
                Result.success(MemberPage(members = listOf(groupMember("did:existing")).toImmutableList(), cursor = null))
            every { actorRepo.recentActors("did:self", any()) } returns
                flowOf(listOf(actorUi("did:existing"), actorUi("did:new")))

            val vm = vm()
            advanceUntilIdle()

            val status = vm.uiState.value.status
            assertTrue(status is AddMembersStatus.Recent, "expected Recent, got $status")
            assertEquals(listOf("did:new"), (status as AddMembersStatus.Recent).items.map { it.did })
        }

    @Test
    fun `recipientToggled_addsThenRemoves`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors(any(), any()) } returns flowOf(listOf(actorUi("did:new")))

            val vm = vm()
            advanceUntilIdle()

            vm.handleEvent(AddMembersEvent.RecipientToggled("did:new"))
            assertEquals(
                listOf("did:new"),
                vm.uiState.value.selected
                    .map { it.did },
            )

            vm.handleEvent(AddMembersEvent.RecipientToggled("did:new"))
            assertTrue(
                vm.uiState.value.selected
                    .isEmpty(),
                "toggling again removes the selection",
            )
        }

    @Test
    fun `recipientToggled_removesPickedDidFromVisibleList`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors(any(), any()) } returns flowOf(listOf(actorUi("did:new"), actorUi("did:other")))

            val vm = vm()
            advanceUntilIdle()

            // Both candidates are visible before any selection.
            assertEquals(
                listOf("did:new", "did:other"),
                (vm.uiState.value.status as AddMembersStatus.Recent).items.map { it.did },
            )

            vm.handleEvent(AddMembersEvent.RecipientToggled("did:new"))
            advanceUntilIdle()

            // The selection-change re-runs the combine so the picked did drops out of the list.
            assertEquals(
                listOf("did:other"),
                (vm.uiState.value.status as AddMembersStatus.Recent).items.map { it.did },
                "a picked did is filtered out of the visible candidates",
            )
        }

    @Test
    fun `recipientRemoved_removesSelectedChip`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors(any(), any()) } returns flowOf(listOf(actorUi("did:new")))

            val vm = vm()
            advanceUntilIdle()

            vm.handleEvent(AddMembersEvent.RecipientToggled("did:new"))
            assertEquals(
                listOf("did:new"),
                vm.uiState.value.selected
                    .map { it.did },
            )

            vm.handleEvent(AddMembersEvent.RecipientRemoved("did:new"))
            assertTrue(
                vm.uiState.value.selected
                    .isEmpty(),
            )
        }

    @Test
    fun `capacity_blocksSelectionBeyondMax`() =
        runTest(mainDispatcher.dispatcher) {
            // Roster of GROUP_MAX_MEMBERS - 1 existing members → one selection hits the cap.
            val existing = (0 until GROUP_MAX_MEMBERS - 1).map { groupMember("did:e$it") }
            fakeChat.getConvoMembersResult = Result.success(MemberPage(members = existing.toImmutableList(), cursor = null))
            every { actorRepo.recentActors(any(), any()) } returns flowOf(listOf(actorUi("did:new"), actorUi("did:other")))

            val vm = vm()
            advanceUntilIdle()

            vm.handleEvent(AddMembersEvent.RecipientToggled("did:new"))
            assertEquals(
                listOf("did:new"),
                vm.uiState.value.selected
                    .map { it.did },
            )
            assertTrue(vm.uiState.value.atCapacity, "selecting the last open slot must set atCapacity")

            // A further toggle of a different did must not add (at capacity).
            vm.handleEvent(AddMembersEvent.RecipientToggled("did:other"))
            assertEquals(
                listOf("did:new"),
                vm.uiState.value.selected
                    .map { it.did },
                "no add past capacity",
            )
        }

    @Test
    fun `addTapped_callsAddMembers_emitsMembersAdded`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors(any(), any()) } returns flowOf(listOf(actorUi("did:new")))

            val vm = vm()
            advanceUntilIdle()
            vm.handleEvent(AddMembersEvent.RecipientToggled("did:new"))

            vm.effects.test {
                vm.handleEvent(AddMembersEvent.AddTapped)
                advanceUntilIdle()

                assertEquals(AddMembersEffect.MembersAdded, awaitItem())
            }
            assertEquals(listOf(convoId to listOf("did:new")), fakeChat.addMembersCalls)
        }

    @Test
    fun `addTapped_failure_mapsToShowError_andClearsSubmitting`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors(any(), any()) } returns flowOf(listOf(actorUi("did:new")))
            fakeChat.addMembersResult =
                Result.failure(XrpcError(errorName = "MemberLimitReached", errorMessage = "full", status = 400))

            val vm = vm()
            advanceUntilIdle()
            vm.handleEvent(AddMembersEvent.RecipientToggled("did:new"))

            vm.effects.test {
                vm.handleEvent(AddMembersEvent.AddTapped)
                advanceUntilIdle()

                assertEquals(AddMembersEffect.ShowError(ChatError.GroupFull), awaitItem())
            }
            assertFalse(vm.uiState.value.isSubmitting, "isSubmitting must reset after failure")
        }

    /**
     * Mutates the VM's [AddGroupMembersViewModel.queryFieldState] and drives the
     * Compose snapshot system so the change reaches the VM's `snapshotFlow`
     * collector. Mirrors `NewChatViewModelTest.setQueryText`.
     */
    @Suppress("unused")
    private fun TestScope.setQueryText(
        vm: AddGroupMembersViewModel,
        text: String,
    ) {
        vm.queryFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
    }
}
