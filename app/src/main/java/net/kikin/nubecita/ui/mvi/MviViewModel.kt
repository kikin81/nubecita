package net.kikin.nubecita.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

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
 * [effects] is delivered exactly once to the first subscriber; effects emitted
 * before a subscriber attaches are buffered until consumed.
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
     * any suspending work MUST be launched into [viewModelScope] (see
     * [launchSafe] and [collectSafely]).
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

    /**
     * Launches [block] in `viewModelScope`. Any thrown throwable other than
     * [CancellationException] is mapped to an effect via [onError] and emitted
     * via [sendEffect]. Cooperative cancellation propagates unchanged.
     */
    protected fun launchSafe(
        onError: (Throwable) -> F,
        block: suspend CoroutineScope.() -> Unit,
    ): Job =
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                sendEffect(onError(t))
            }
        }

    /**
     * Collects this flow into `viewModelScope`, running [action] for every
     * element. If the upstream flow throws, [onError] produces an effect that
     * is emitted once via [sendEffect]; [CancellationException] propagates
     * unchanged.
     */
    protected fun <T> Flow<T>.collectSafely(
        onError: (Throwable) -> F,
        action: suspend (T) -> Unit,
    ): Job =
        onEach(action)
            .catch { t ->
                if (t is CancellationException) throw t
                sendEffect(onError(t))
            }.launchIn(viewModelScope)
}
