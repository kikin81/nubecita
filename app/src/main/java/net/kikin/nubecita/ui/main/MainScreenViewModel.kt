package net.kikin.nubecita.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.kikin.nubecita.data.DataRepository
import net.kikin.nubecita.ui.main.MainScreenUiState.Success
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel
    @Inject
    constructor(
        dataRepository: DataRepository,
    ) : ViewModel() {
        val uiState: StateFlow<MainScreenUiState> =
            dataRepository.data
                .map<List<String>, MainScreenUiState>(::Success)
                .catch { emit(MainScreenUiState.Error(it)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)
    }

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState

    data class Error(
        val throwable: Throwable,
    ) : MainScreenUiState

    data class Success(
        val data: List<String>,
    ) : MainScreenUiState
}
