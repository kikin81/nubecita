package net.kikin.nubecita.ui.main

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.data.DataRepository
import net.kikin.nubecita.ui.mvi.Async
import net.kikin.nubecita.ui.mvi.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainScreenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is Loading before repository emits`() {
        val viewModel = MainScreenViewModel(FakeRepository(flow { /* never emits */ }))
        assertEquals(Async.Loading, viewModel.uiState.value.data)
    }

    @Test
    fun `repository emission maps to Async Success with immutable list`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = MainScreenViewModel(FakeRepository(flow { emit(listOf("Sample")) }))
            advanceUntilIdle()

            assertEquals(Async.Success(persistentListOf("Sample")), viewModel.uiState.value.data)
        }

    @Test
    fun `repository error maps to ShowError effect and Async Failure state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cause = RuntimeException("DB down")
            val viewModel = MainScreenViewModel(FakeRepository(flow { throw cause }))
            advanceUntilIdle()

            assertEquals(Async.Failure(cause), viewModel.uiState.value.data)

            val effect = viewModel.effects.first()
            assertTrue(effect is MainScreenEffect.ShowError)
            assertEquals("DB down", (effect as MainScreenEffect.ShowError).message)
        }

    @Test
    fun `Refresh resets state to Loading and emits no effect on happy path`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = MainScreenViewModel(FakeRepository(flow { emit(listOf("A")) }))
            advanceUntilIdle()
            assertEquals(Async.Success(persistentListOf("A")), viewModel.uiState.value.data)

            viewModel.handleEvent(MainScreenEvent.Refresh)
            // Immediately after Refresh, before the collector re-runs, state is Loading.
            assertEquals(Async.Loading, viewModel.uiState.value.data)

            advanceUntilIdle()
            assertEquals(Async.Success(persistentListOf("A")), viewModel.uiState.value.data)

            val effect = withTimeoutOrNull(timeMillis = 50) { viewModel.effects.first() }
            assertNull(effect)
        }
}

private class FakeRepository(
    override val data: Flow<List<String>>,
) : DataRepository
