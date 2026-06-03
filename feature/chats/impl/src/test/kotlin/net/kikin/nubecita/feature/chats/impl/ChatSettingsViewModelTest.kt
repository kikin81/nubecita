package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.impl.data.ChatSettingsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatSettingsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private fun createVm(
        repository: ChatSettingsRepository =
            mockk(relaxed = true) {
                coEvery { getAllowIncoming() } returns Result.success(AllowIncoming.Following)
                coEvery { setAllowIncoming(any()) } returns Result.success(Unit)
            },
    ) = ChatSettingsViewModel(repository = repository)

    @Test
    fun `init loads the current selection into Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                mockk<ChatSettingsRepository>(relaxed = true) {
                    coEvery { getAllowIncoming() } returns Result.success(AllowIncoming.Everyone)
                }
            val vm = createVm(repo)
            advanceUntilIdle()

            assertEquals(
                ChatSettingsLoadStatus.Loaded(selected = AllowIncoming.Everyone),
                vm.uiState.value.status,
            )
        }

    @Test
    fun `load failure shows LoadError, RetryLoad reloads`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                mockk<ChatSettingsRepository>(relaxed = true) {
                    coEvery { getAllowIncoming() } returnsMany
                        listOf(
                            Result.failure(IOException("net")),
                            Result.success(AllowIncoming.NoOne),
                        )
                }
            val vm = createVm(repo)
            advanceUntilIdle()
            assertEquals(ChatSettingsLoadStatus.LoadError, vm.uiState.value.status)

            vm.handleEvent(ChatSettingsEvent.RetryLoad)
            advanceUntilIdle()
            assertEquals(
                ChatSettingsLoadStatus.Loaded(selected = AllowIncoming.NoOne),
                vm.uiState.value.status,
            )
        }

    @Test
    fun `OptionSelected optimistically updates selection and writes the value`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                mockk<ChatSettingsRepository>(relaxed = true) {
                    coEvery { getAllowIncoming() } returns Result.success(AllowIncoming.Following)
                    coEvery { setAllowIncoming(any()) } returns Result.success(Unit)
                }
            val vm = createVm(repo)
            advanceUntilIdle()

            vm.handleEvent(ChatSettingsEvent.OptionSelected(AllowIncoming.Everyone))
            advanceUntilIdle()

            assertEquals(
                ChatSettingsLoadStatus.Loaded(selected = AllowIncoming.Everyone, isSaving = false),
                vm.uiState.value.status,
            )
            coVerify(exactly = 1) { repo.setAllowIncoming(AllowIncoming.Everyone) }
        }

    @Test
    fun `OptionSelected write failure reverts selection and emits ShowSaveError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                mockk<ChatSettingsRepository>(relaxed = true) {
                    coEvery { getAllowIncoming() } returns Result.success(AllowIncoming.Following)
                    coEvery { setAllowIncoming(AllowIncoming.NoOne) } returns Result.failure(IOException("net"))
                }
            val vm = createVm(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ChatSettingsEvent.OptionSelected(AllowIncoming.NoOne))
                advanceUntilIdle()
                assertEquals(ChatSettingsEffect.ShowSaveError, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            // Reverted to the last-confirmed value.
            assertEquals(
                ChatSettingsLoadStatus.Loaded(selected = AllowIncoming.Following, isSaving = false),
                vm.uiState.value.status,
            )
        }

    @Test
    fun `OptionSelected with the already-selected value is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                mockk<ChatSettingsRepository>(relaxed = true) {
                    coEvery { getAllowIncoming() } returns Result.success(AllowIncoming.Following)
                }
            val vm = createVm(repo)
            advanceUntilIdle()

            vm.handleEvent(ChatSettingsEvent.OptionSelected(AllowIncoming.Following))
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.setAllowIncoming(any()) }
        }
}
