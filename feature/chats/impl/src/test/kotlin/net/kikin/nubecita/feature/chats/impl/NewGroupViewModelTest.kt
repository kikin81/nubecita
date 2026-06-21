package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class NewGroupViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val actorRepo = mockk<ActorRepository>(relaxed = true)
    private val fakeChat = FakeChatRepository()
    private val session =
        mockk<SessionStateProvider> {
            every { state } returns MutableStateFlow(SessionState.SignedIn(handle = "me", did = "did:self"))
        }

    private fun vm() = NewGroupViewModel(actorRepo, fakeChat, session)

    private fun actorUi(did: String) = ActorUi(did, "$did.bsky", null, null, canMessage = true)

    @Test
    fun `blankName_canCreateFalse_thenNameAndMemberMakesItTrue`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors("did:self", any()) } returns flowOf(listOf(actorUi("did:a")))

            val vm = vm()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.canCreate, "blank name + no members → cannot create")

            setName(vm, "Trips")
            vm.handleEvent(NewGroupEvent.RecipientToggled("did:a"))
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isNameValid)
            assertTrue(vm.uiState.value.canCreate, "valid name + one member → can create")
        }

    @Test
    fun `nameLength_boundaryAtMaxGraphemes`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            advanceUntilIdle()

            setName(vm, "a".repeat(GROUP_NAME_COUNTER_THRESHOLD + 7)) // 110
            assertEquals(110, vm.uiState.value.nameGraphemeCount)
            assertTrue(vm.uiState.value.nameGraphemeCount > GROUP_NAME_COUNTER_THRESHOLD, "crosses counter threshold")
            assertTrue(vm.uiState.value.isNameValid)

            setName(vm, "a".repeat(GROUP_NAME_MAX_GRAPHEMES)) // 128
            assertEquals(GROUP_NAME_MAX_GRAPHEMES, vm.uiState.value.nameGraphemeCount)
            assertTrue(vm.uiState.value.isNameValid, "exactly max is valid")

            setName(vm, "a".repeat(GROUP_NAME_MAX_GRAPHEMES + 1)) // 129
            assertFalse(vm.uiState.value.isNameValid, "one past max is invalid")
        }

    @Test
    fun `blankQuery_recentExcludesSelf_toggleAddsThenRemoves`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors("did:self", any()) } returns
                flowOf(listOf(actorUi("did:a"), actorUi("did:self")))

            val vm = vm()
            advanceUntilIdle()

            val status = vm.uiState.value.status
            assertTrue(status is NewGroupStatus.Recent, "expected Recent, got $status")
            assertEquals(listOf("did:a"), (status as NewGroupStatus.Recent).items.map { it.did }, "self is filtered out")

            vm.handleEvent(NewGroupEvent.RecipientToggled("did:a"))
            assertEquals(
                listOf("did:a"),
                vm.uiState.value.selected
                    .map { it.did },
            )

            vm.handleEvent(NewGroupEvent.RecipientToggled("did:a"))
            assertTrue(
                vm.uiState.value.selected
                    .isEmpty(),
                "toggling again removes the selection",
            )
        }

    @Test
    fun `capacity_blocksSelectionBeyondMax`() =
        runTest(mainDispatcher.dispatcher) {
            // A brand-new group has room for GROUP_MAX_MEMBERS - 1 picks.
            val recents = (0 until GROUP_MAX_MEMBERS).map { actorUi("did:r$it") }
            every { actorRepo.recentActors("did:self", any()) } returns flowOf(recents)

            val vm = vm()
            advanceUntilIdle()

            // Fill all but the last open slot, then take the last.
            for (i in 0 until GROUP_MAX_MEMBERS - 1) {
                vm.handleEvent(NewGroupEvent.RecipientToggled("did:r$i"))
                advanceUntilIdle()
            }

            assertEquals(GROUP_MAX_MEMBERS - 1, vm.uiState.value.selected.size)
            assertTrue(vm.uiState.value.atCapacity, "filling the last open slot sets atCapacity")

            // A further toggle of a new did is dropped.
            vm.handleEvent(NewGroupEvent.RecipientToggled("did:r${GROUP_MAX_MEMBERS - 1}"))
            advanceUntilIdle()
            assertEquals(GROUP_MAX_MEMBERS - 1, vm.uiState.value.selected.size, "no add past capacity")
        }

    @Test
    fun `createTapped_callsCreateGroup_emitsGroupCreated`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors("did:self", any()) } returns flowOf(listOf(actorUi("did:a")))
            fakeChat.createGroupResult = Result.success("convo:new")

            val vm = vm()
            advanceUntilIdle()
            setName(vm, "Trips")
            vm.handleEvent(NewGroupEvent.RecipientToggled("did:a"))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NewGroupEvent.CreateTapped)
                advanceUntilIdle()

                assertEquals(NewGroupEffect.GroupCreated("convo:new"), awaitItem())
            }
            assertEquals("Trips" to listOf("did:a"), fakeChat.createGroupCalls.last())
        }

    @Test
    fun `createTapped_failure_mapsToShowError_andClearsSubmitting`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors("did:self", any()) } returns flowOf(listOf(actorUi("did:a")))
            fakeChat.createGroupResult =
                Result.failure(XrpcError(errorName = "MemberLimitReached", errorMessage = "", status = 400))

            val vm = vm()
            advanceUntilIdle()
            setName(vm, "Trips")
            vm.handleEvent(NewGroupEvent.RecipientToggled("did:a"))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NewGroupEvent.CreateTapped)
                advanceUntilIdle()

                assertEquals(NewGroupEffect.ShowError(ChatError.GroupFull), awaitItem())
            }
            assertFalse(vm.uiState.value.isSubmitting, "isSubmitting must reset after failure")
        }

    @Test
    fun `whileSubmitting_pickerEditsAndSecondCreate_areIgnored`() =
        runTest(mainDispatcher.dispatcher) {
            every { actorRepo.recentActors("did:self", any()) } returns
                flowOf(listOf(actorUi("did:a"), actorUi("did:b")))
            val gate = CompletableDeferred<Unit>()
            fakeChat.createGroupGate = gate

            val vm = vm()
            advanceUntilIdle()
            setName(vm, "Trips")
            vm.handleEvent(NewGroupEvent.RecipientToggled("did:a"))
            advanceUntilIdle()

            // Start the create — it parks on the gate, holding isSubmitting = true.
            vm.handleEvent(NewGroupEvent.CreateTapped)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isSubmitting, "submit in flight")

            // Picker edits and a second create are all dropped while submitting.
            vm.handleEvent(NewGroupEvent.RecipientToggled("did:b"))
            vm.handleEvent(NewGroupEvent.RecipientRemoved("did:a"))
            vm.handleEvent(NewGroupEvent.CreateTapped)
            advanceUntilIdle()

            assertEquals(
                listOf("did:a"),
                vm.uiState.value.selected
                    .map { it.did },
                "selection unchanged while submitting",
            )
            assertEquals(1, fakeChat.createGroupCalls.size, "no second createGroup while submitting")

            // Release the gate; the original create completes.
            vm.effects.test {
                gate.complete(Unit)
                advanceUntilIdle()
                assertEquals(NewGroupEffect.GroupCreated("convo:new"), awaitItem())
            }
        }

    /** Mutates [NewGroupViewModel.nameFieldState] and drives the snapshot system to the VM. */
    private fun TestScope.setName(
        vm: NewGroupViewModel,
        text: String,
    ) {
        vm.nameFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
    }

    /** Mutates [NewGroupViewModel.queryFieldState] and drives the snapshot system to the VM. */
    @Suppress("unused")
    private fun TestScope.setQuery(
        vm: NewGroupViewModel,
        text: String,
    ) {
        vm.queryFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
    }
}
