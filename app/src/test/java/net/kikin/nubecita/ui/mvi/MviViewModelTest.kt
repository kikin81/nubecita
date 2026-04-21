package net.kikin.nubecita.ui.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MviViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
        runTest {
            val vm = Vm()
            vm.emitEffectDirectly(Effect.Ponged)
            // Let the launched sendEffect coroutine run so the channel receives the item.
            testScheduler.runCurrent()

            val received = vm.effects.first()
            assertEquals(Effect.Ponged, received)
        }

    @Test
    fun `effect delivered once is not re-delivered to later collector`() =
        runTest {
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
        runTest {
            val vm = Vm()
            vm.handleEvent(Event.Increment)
            vm.handleEvent(Event.Ping)
            testScheduler.runCurrent()

            assertEquals(1, vm.uiState.value.count)
            assertEquals(Effect.Ponged, vm.effects.first())
        }
}
