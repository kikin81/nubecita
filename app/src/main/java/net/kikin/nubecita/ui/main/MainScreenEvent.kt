package net.kikin.nubecita.ui.main

import net.kikin.nubecita.core.common.mvi.UiEvent

sealed interface MainScreenEvent : UiEvent {
    data object Refresh : MainScreenEvent
}
