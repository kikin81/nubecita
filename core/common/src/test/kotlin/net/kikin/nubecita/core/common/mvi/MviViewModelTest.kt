package net.kikin.nubecita.core.common.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class MviViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private data class State(
        val count: Int = 0,
    ) : UiState

    private sealed interface Event : UiEvent {
        data object Increment : Event

        data object Ping : Event
    }

    private sealed interface Effect : UiEffect {
        data object Ponged : Effect
    }

    private class Vm : MviViewModel<State, Event, Effect>(State()) {
        override fun handleEvent(event: Event) {
            when (event) {
                Event.Increment -> setState { copy(count = count + 1) }
                Event.Ping -> sendEffect(Effect.Ponged)
            }
        }

        fun emitEffectDirectly(effect: Effect) = sendEffect(effect)
    }

    @Test
    fun `initial state is exposed via uiState`() {
        val vm = Vm()
        assertEquals(State(count = 0), vm.uiState.value)
    }

    @Test
    fun `setState reducer updates uiState synchronously`() {
        val vm = Vm()
        vm.handleEvent(Event.Increment)
        vm.handleEvent(Event.Increment)
        assertEquals(State(count = 2), vm.uiState.value)
    }

    @Test
    fun `effect buffered before collector subscribes is delivered`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = Vm()
            vm.emitEffectDirectly(Effect.Ponged)
            // Let the launched sendEffect coroutine run so the channel receives the item.
            testScheduler.runCurrent()

            val received = vm.effects.first()
            assertEquals(Effect.Ponged, received)
        }

    @Test
    fun `effect delivered once is not re-delivered to later collector`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = Vm()
            vm.handleEvent(Event.Ping)
            testScheduler.runCurrent()

            val first = vm.effects.first()
            assertEquals(Effect.Ponged, first)

            val second =
                withTimeoutOrNull(timeMillis = 50) {
                    vm.effects.first()
                }
            assertNull(second)
        }

    @Test
    fun `handleEvent dispatches on event type`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = Vm()
            vm.handleEvent(Event.Increment)
            vm.handleEvent(Event.Ping)
            testScheduler.runCurrent()

            assertEquals(1, vm.uiState.value.count)
            assertEquals(Effect.Ponged, vm.effects.first())
        }
}
