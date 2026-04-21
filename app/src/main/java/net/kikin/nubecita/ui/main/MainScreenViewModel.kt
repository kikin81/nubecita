package net.kikin.nubecita.ui.main

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import net.kikin.nubecita.data.DataRepository
import net.kikin.nubecita.ui.mvi.Async
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
            setState { copy(data = Async.Loading) }
            collectionJob =
                dataRepository.data.collectSafely(
                    onError = {
                        setState { copy(data = Async.Failure(it)) }
                        MainScreenEffect.ShowError(it.message ?: "Unknown error")
                    },
                ) { items ->
                    setState { copy(data = Async.Success(items.toImmutableList())) }
                }
        }
    }
