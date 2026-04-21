package net.kikin.nubecita.ui.mvi

/**
 * Marker interface for UI intents dispatched to an [MviViewModel] via
 * [MviViewModel.handleEvent]. Implementations are typically `sealed interface`
 * hierarchies scoped to a single screen.
 */
interface UiEvent
