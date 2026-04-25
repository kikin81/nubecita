package net.kikin.nubecita.core.common.mvi

/**
 * Marker interface for a screen's observable UI state.
 *
 * Implementations SHOULD be immutable `data class`es so reducers produced via
 * [MviViewModel.setState] are pure `copy` transformations and recomposition is
 * skippable for unchanged fields.
 */
interface UiState
