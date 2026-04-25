package net.kikin.nubecita.ui.main

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiState

data class MainScreenState(
    val items: ImmutableList<String> = persistentListOf(),
    val isLoading: Boolean = true,
) : UiState
