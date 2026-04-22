package net.kikin.nubecita.ui.main

import net.kikin.nubecita.ui.mvi.UiEvent

sealed interface MainScreenEvent : UiEvent {
    data object Refresh : MainScreenEvent
}
