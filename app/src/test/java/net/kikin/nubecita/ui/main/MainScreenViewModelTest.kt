package net.kikin.nubecita.ui.main

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.DataRepository
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun uiState_initiallyLoading() =
        runTest {
            val viewModel = MainScreenViewModel(FakeMyModelRepository())
            assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
        }

    @Test
    fun uiState_onItemSaved_isDisplayed() =
        runTest {
            val viewModel = MainScreenViewModel(FakeMyModelRepository())
            assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
        }
}

private class FakeMyModelRepository : DataRepository {
    override val data: Flow<List<String>> = flow { emit(listOf("Sample")) }
}
