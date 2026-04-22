package net.kikin.nubecita.ui.main

import net.kikin.nubecita.ui.mvi.UiEffect

sealed interface MainScreenEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : MainScreenEffect
}
