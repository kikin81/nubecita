package net.kikin.nubecita.ui.main

import net.kikin.nubecita.core.common.mvi.UiEffect

sealed interface MainScreenEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : MainScreenEffect
}
