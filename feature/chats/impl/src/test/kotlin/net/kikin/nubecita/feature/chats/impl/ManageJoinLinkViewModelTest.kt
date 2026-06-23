package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.api.ManageJoinLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ManageJoinLinkViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val route = ManageJoinLink(convoId = "convo-1")

    private fun link(
        enabled: Boolean = true,
        joinRule: JoinRule = JoinRule.Anyone,
        requireApproval: Boolean = true,
    ) = JoinLinkUi(
        code = "code-1",
        url = "https://nubecita.app/group/join/code-1",
        enabled = enabled,
        joinRule = joinRule,
        requireApproval = requireApproval,
        createdAt = Instant.parse("2026-05-13T12:00:00Z"),
    )

    private fun vm(fake: FakeChatRepository) = ManageJoinLinkViewModel(route, fake)

    @Test
    fun `load surfaces existing link`() =
        runTest(mainDispatcher.dispatcher) {
            val fake = FakeChatRepository().apply { getJoinLinkResult = Result.success(link()) }
            val model = vm(fake)
            advanceUntilIdle()
            assertEquals(ManageJoinLinkStatus.Loaded(link()), model.uiState.value.status)
        }

    @Test
    fun `load with no link shows create state`() =
        runTest(mainDispatcher.dispatcher) {
            val fake = FakeChatRepository().apply { getJoinLinkResult = Result.success(null) }
            val model = vm(fake)
            advanceUntilIdle()
            assertEquals(ManageJoinLinkStatus.Loaded(null), model.uiState.value.status)
        }

    @Test
    fun `load failure is sticky error and retry reloads`() =
        runTest(mainDispatcher.dispatcher) {
            val fake = FakeChatRepository().apply { getJoinLinkResult = Result.failure(RuntimeException("x")) }
            val model = vm(fake)
            advanceUntilIdle()
            assertTrue(model.uiState.value.status is ManageJoinLinkStatus.Error)
            fake.getJoinLinkResult = Result.success(link())
            model.handleEvent(ManageJoinLinkEvent.Retry)
            advanceUntilIdle()
            assertEquals(ManageJoinLinkStatus.Loaded(link()), model.uiState.value.status)
        }

    @Test
    fun `create success moves to loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(null)
                    createJoinLinkResult = Result.success(link())
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.CreateTapped(JoinRule.Anyone, requireApproval = true))
            advanceUntilIdle()
            assertEquals(ManageJoinLinkStatus.Loaded(link()), model.uiState.value.status)
            assertEquals(Triple("convo-1", JoinRule.Anyone, true), fake.createJoinLinkCalls.single())
        }

    @Test
    fun `create failure stays in create state and emits error`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(null)
                    createJoinLinkResult = Result.failure(RuntimeException("x"))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.effects.test {
                model.handleEvent(ManageJoinLinkEvent.CreateTapped(JoinRule.Anyone, requireApproval = true))
                advanceUntilIdle()
                assertTrue(awaitItem() is ManageJoinLinkEffect.ShowError)
            }
            assertEquals(ManageJoinLinkStatus.Loaded(null), model.uiState.value.status)
            assertFalse(model.uiState.value.mutationInFlight)
        }

    @Test
    fun `disable optimistically flips then reconciles`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(link(enabled = true))
                    disableJoinLinkResult = Result.success(link(enabled = false))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.DisableTapped)
            advanceUntilIdle()
            assertEquals(listOf("convo-1"), fake.disableJoinLinkCalls)
            assertEquals(false, (model.uiState.value.status as ManageJoinLinkStatus.Loaded).link?.enabled)
        }

    @Test
    fun `disable failure rolls back to enabled`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(link(enabled = true))
                    disableJoinLinkResult = Result.failure(RuntimeException("x"))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.DisableTapped)
            advanceUntilIdle()
            assertEquals(true, (model.uiState.value.status as ManageJoinLinkStatus.Loaded).link?.enabled)
            assertFalse(model.uiState.value.mutationInFlight)
        }

    @Test
    fun `requireApproval edit sends only that field`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(link(requireApproval = true))
                    editJoinLinkResult = Result.success(link(requireApproval = false))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.RequireApprovalChanged(requireApproval = false))
            advanceUntilIdle()
            assertEquals(Triple("convo-1", null, false), fake.editJoinLinkCalls.single())
        }

    @Test
    fun `joinRule edit sends only that field`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(link(joinRule = JoinRule.Anyone))
                    editJoinLinkResult = Result.success(link(joinRule = JoinRule.FollowedByOwner))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.JoinRuleChanged(JoinRule.FollowedByOwner))
            advanceUntilIdle()
            assertEquals(Triple("convo-1", JoinRule.FollowedByOwner, null), fake.editJoinLinkCalls.single())
        }

    @Test
    fun `edit failure rolls back to prior rule`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(link(joinRule = JoinRule.Anyone))
                    editJoinLinkResult = Result.failure(RuntimeException("x"))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.JoinRuleChanged(JoinRule.FollowedByOwner))
            advanceUntilIdle()
            assertEquals(JoinRule.Anyone, (model.uiState.value.status as ManageJoinLinkStatus.Loaded).link?.joinRule)
        }

    @Test
    fun `in-flight guard drops a second concurrent mutation`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(link(enabled = true))
                    disableJoinLinkResult = Result.success(link(enabled = false))
                    joinLinkMutationGate = CompletableDeferred()
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.DisableTapped) // suspends on the gate
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.EnableTapped) // dropped by the guard
            advanceUntilIdle()
            fake.joinLinkMutationGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("convo-1"), fake.disableJoinLinkCalls)
            assertTrue(fake.enableJoinLinkCalls.isEmpty())
        }

    @Test
    fun `unsupported link drops all setting events`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getJoinLinkResult = Result.success(link(joinRule = JoinRule.Unsupported))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(ManageJoinLinkEvent.RequireApprovalChanged(false))
            model.handleEvent(ManageJoinLinkEvent.JoinRuleChanged(JoinRule.Anyone))
            model.handleEvent(ManageJoinLinkEvent.DisableTapped)
            model.handleEvent(ManageJoinLinkEvent.EnableTapped)
            advanceUntilIdle()
            assertTrue(fake.editJoinLinkCalls.isEmpty())
            assertTrue(fake.disableJoinLinkCalls.isEmpty())
            assertTrue(fake.enableJoinLinkCalls.isEmpty())
            assertEquals(
                JoinRule.Unsupported,
                (model.uiState.value.status as ManageJoinLinkStatus.Loaded).link?.joinRule,
            )
        }
}
