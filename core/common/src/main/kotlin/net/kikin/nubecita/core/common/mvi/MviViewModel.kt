package net.kikin.nubecita.core.common.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base [ViewModel] for the app's MVI architecture.
 *
 * Subclasses:
 *  - Declare per-screen `UiState` / `UiEvent` / `UiEffect` types implementing the markers.
 *  - Provide an initial state via the constructor.
 *  - Implement [handleEvent] to dispatch on the event type, calling [setState] to
 *    transform state or [sendEffect] to emit one-shot effects.
 *
 * [uiState] is a conflated hot stream — every subscriber sees the latest value.
 * [effects] backs a [Channel] via [receiveAsFlow]: each emitted effect is
 * delivered to at most one collector. Effects emitted before any subscriber
 * attaches are buffered until consumed. Screens MUST collect [effects] from
 * exactly one place (typically a single `LaunchedEffect` in the screen's
 * root composable); concurrent collectors compete for elements and will each
 * see a disjoint subset.
 */
abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(
    initialState: S,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)
    val effects: Flow<F> = _effects.receiveAsFlow()

    /**
     * Dispatches an incoming UI intent. Implementations MUST return quickly —
     * any suspending work MUST be launched into [viewModelScope] with the
     * usual `Flow.onEach { ... }.catch { ... }.launchIn(viewModelScope)` /
     * `viewModelScope.launch { try { ... } catch { ... } }` idioms.
     */
    abstract fun handleEvent(event: E)

    /**
     * Atomically applies [reducer] to the current state. The reducer MUST be
     * pure: no side effects, no `sendEffect` calls, no suspension.
     */
    protected fun setState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    /**
     * Buffers [effect] for delivery to the next collector. Safe to call from
     * any coroutine; emissions are serialized via an internal [Channel].
     */
    protected fun sendEffect(effect: F) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }
}
