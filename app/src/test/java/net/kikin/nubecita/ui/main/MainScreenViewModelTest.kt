package net.kikin.nubecita.ui.main

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.DataRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainScreenViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial state is Loading with no items`() {
        val viewModel = MainScreenViewModel(FakeRepository(flow { /* never emits */ }))
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(persistentListOf<String>(), viewModel.uiState.value.items)
    }

    @Test
    fun `repository emission populates items and clears isLoading`() =
        runTest(mainDispatcher.dispatcher) {
            val viewModel = MainScreenViewModel(FakeRepository(flow { emit(listOf("Sample")) }))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(persistentListOf("Sample"), state.items)
            assertEquals(false, state.isLoading)
        }

    @Test
    fun `repository error clears isLoading and emits ShowError effect`() =
        runTest(mainDispatcher.dispatcher) {
            val viewModel =
                MainScreenViewModel(FakeRepository(flow { throw RuntimeException("DB down") }))
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isLoading)
            assertEquals(persistentListOf<String>(), viewModel.uiState.value.items)

            val effect = viewModel.effects.first()
            assertTrue(effect is MainScreenEffect.ShowError)
            assertEquals("DB down", (effect as MainScreenEffect.ShowError).message)
        }

    @Test
    fun `Refresh resets state to Loading and emits no effect on happy path`() =
        runTest(mainDispatcher.dispatcher) {
            val viewModel = MainScreenViewModel(FakeRepository(flow { emit(listOf("A")) }))
            advanceUntilIdle()
            assertEquals(persistentListOf("A"), viewModel.uiState.value.items)
            assertEquals(false, viewModel.uiState.value.isLoading)

            viewModel.handleEvent(MainScreenEvent.Refresh)
            // Immediately after Refresh, before the collector re-runs, isLoading is true.
            assertTrue(viewModel.uiState.value.isLoading)

            advanceUntilIdle()
            assertEquals(persistentListOf("A"), viewModel.uiState.value.items)
            assertEquals(false, viewModel.uiState.value.isLoading)

            val effect = withTimeoutOrNull(timeMillis = 50) { viewModel.effects.first() }
            assertNull(effect)
        }
}

private class FakeRepository(
    override val data: Flow<List<String>>,
) : DataRepository
