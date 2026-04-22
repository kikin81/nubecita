package net.kikin.nubecita.ui.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.data.DataRepository
import net.kikin.nubecita.ui.mvi.MviViewModel
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel
    @Inject
    constructor(
        private val dataRepository: DataRepository,
    ) : MviViewModel<MainScreenState, MainScreenEvent, MainScreenEffect>(MainScreenState()) {
        private var collectionJob: Job? = null

        init {
            startCollection()
        }

        override fun handleEvent(event: MainScreenEvent) {
            when (event) {
                MainScreenEvent.Refresh -> startCollection()
            }
        }

        private fun startCollection() {
            collectionJob?.cancel()
            setState { copy(isLoading = true) }
            collectionJob =
                dataRepository.data
                    .onEach { items ->
                        setState { copy(items = items.toImmutableList(), isLoading = false) }
                    }.catch { e ->
                        setState { copy(isLoading = false) }
                        sendEffect(MainScreenEffect.ShowError(e.message ?: "Unknown error"))
                    }.launchIn(viewModelScope)
        }
    }
