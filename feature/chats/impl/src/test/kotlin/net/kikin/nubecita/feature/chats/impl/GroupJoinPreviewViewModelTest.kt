package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class GroupJoinPreviewViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val route = GroupJoinPreview(code = "code-1")

    private val info =
        GroupPublicInfoUi(
            name = "Book Club",
            memberCount = 7,
            ownerDisplayName = "Alice",
            ownerHandle = "alice.bsky.social",
            ownerAvatarUrl = null,
            requireApproval = true,
        )

    private fun vm(fake: FakeChatRepository) = GroupJoinPreviewViewModel(route, fake)

    @Test
    fun `load surfaces the preview`() =
        runTest(mainDispatcher.dispatcher) {
            val fake = FakeChatRepository().apply { getGroupPublicInfoResult = Result.success(info) }
            val model = vm(fake)
            advanceUntilIdle()
            assertEquals(GroupJoinPreviewStatus.Loaded(info), model.uiState.value.status)
            assertEquals(listOf("code-1"), fake.getGroupPublicInfoCalls)
        }

    @Test
    fun `load failure is sticky error and retry reloads`() =
        runTest(mainDispatcher.dispatcher) {
            val fake = FakeChatRepository().apply { getGroupPublicInfoResult = Result.failure(RuntimeException("x")) }
            val model = vm(fake)
            advanceUntilIdle()
            assertTrue(model.uiState.value.status is GroupJoinPreviewStatus.Error)
            fake.getGroupPublicInfoResult = Result.success(info)
            model.handleEvent(GroupJoinPreviewEvent.Retry)
            advanceUntilIdle()
            assertEquals(GroupJoinPreviewStatus.Loaded(info), model.uiState.value.status)
        }

    @Test
    fun `join that joins directly emits NavigateToConvo`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getGroupPublicInfoResult = Result.success(info)
                    requestJoinResult = Result.success(JoinResult.Joined("convo-9"))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.effects.test {
                model.handleEvent(GroupJoinPreviewEvent.JoinTapped)
                advanceUntilIdle()
                assertEquals(GroupJoinPreviewEffect.NavigateToConvo("convo-9"), awaitItem())
            }
            assertFalse(model.uiState.value.joinInFlight)
        }

    @Test
    fun `join that is pending shows RequestSent`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getGroupPublicInfoResult = Result.success(info)
                    requestJoinResult = Result.success(JoinResult.Pending)
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(GroupJoinPreviewEvent.JoinTapped)
            advanceUntilIdle()
            assertEquals(GroupJoinPreviewStatus.RequestSent, model.uiState.value.status)
        }

    @Test
    fun `join failure emits error and stays loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getGroupPublicInfoResult = Result.success(info)
                    requestJoinResult = Result.failure(RuntimeException("x"))
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.effects.test {
                model.handleEvent(GroupJoinPreviewEvent.JoinTapped)
                advanceUntilIdle()
                assertTrue(awaitItem() is GroupJoinPreviewEffect.ShowError, "expected ShowError effect")
            }
            assertEquals(GroupJoinPreviewStatus.Loaded(info), model.uiState.value.status)
            assertFalse(model.uiState.value.joinInFlight)
        }

    @Test
    fun `in-flight guard drops a second concurrent join`() =
        runTest(mainDispatcher.dispatcher) {
            val fake =
                FakeChatRepository().apply {
                    getGroupPublicInfoResult = Result.success(info)
                    requestJoinResult = Result.success(JoinResult.Pending)
                    requestJoinGate = CompletableDeferred()
                }
            val model = vm(fake)
            advanceUntilIdle()
            model.handleEvent(GroupJoinPreviewEvent.JoinTapped) // suspends on the gate
            advanceUntilIdle()
            model.handleEvent(GroupJoinPreviewEvent.JoinTapped) // dropped by the guard
            fake.requestJoinGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("code-1"), fake.requestJoinCalls)
        }
}
