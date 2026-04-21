package net.kikin.nubecita.ui.main

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.ui.mvi.Async
import net.kikin.nubecita.ui.mvi.UiState

data class MainScreenState(
    val data: Async<ImmutableList<String>> = Async.Uninitialized,
) : UiState
