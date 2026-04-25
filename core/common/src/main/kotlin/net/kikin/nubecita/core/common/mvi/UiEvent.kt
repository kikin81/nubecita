package net.kikin.nubecita.core.common.mvi

/**
 * Marker interface for UI intents dispatched to an [MviViewModel] via
 * [MviViewModel.handleEvent]. Implementations are typically `sealed interface`
 * hierarchies scoped to a single screen.
 */
interface UiEvent
