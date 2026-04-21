package net.kikin.nubecita.ui.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class CoroutineHelpersTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private data class State(
        val items: List<Int> = emptyList(),
    ) : UiState

    private sealed interface Event : UiEvent

    private sealed interface Effect : UiEffect {
        data class Error(
            val message: String,
        ) : Effect
    }

    private class Vm : MviViewModel<State, Event, Effect>(State()) {
        override fun handleEvent(event: Event) = Unit

        fun runSafe(block: suspend () -> Unit) =
            launchSafe(onError = { Effect.Error(it.message ?: "unknown") }) {
                block()
            }

        fun <T> collect(
            flow: Flow<T>,
            action: suspend (T) -> Unit,
        ) = flow.collectSafely(
            onError = { Effect.Error(it.message ?: "unknown") },
            action = action,
        )
    }

    @Test
    fun `launchSafe with successful block emits no effect`() =
        runTest {
            val vm = Vm()
            val job = vm.runSafe { /* no-op */ }
            job.join()

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }

    @Test
    fun `launchSafe maps thrown exception to an effect exactly once`() =
        runTest {
            val vm = Vm()
            val job = vm.runSafe { throw IOException("boom") }
            job.join()

            val first = vm.effects.first()
            assertEquals(Effect.Error("boom"), first)

            val second = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(second)
        }

    @Test
    fun `launchSafe does not map CancellationException to an effect`() =
        runTest {
            val vm = Vm()
            val job =
                vm.runSafe {
                    throw kotlinx.coroutines.CancellationException("cancelled")
                }
            job.join()

            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }

    @Test
    fun `collectSafely runs action for pre-error emissions then emits error effect once`() =
        runTest {
            val vm = Vm()
            val collected = mutableListOf<Int>()
            val upstream =
                flow {
                    emit(1)
                    emit(2)
                    throw IOException("stream failed")
                }

            vm.collect(upstream) { collected += it }.join()

            assertEquals(listOf(1, 2), collected)

            val first = vm.effects.first()
            assertEquals(Effect.Error("stream failed"), first)

            val second = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(second)
        }

    @Test
    fun `collectSafely happy path runs action for every emission and emits no effect`() =
        runTest {
            val vm = Vm()
            val collected = mutableListOf<Int>()
            vm.collect(flowOf(1, 2, 3)) { collected += it }.join()

            assertEquals(listOf(1, 2, 3), collected)
            val effect = withTimeoutOrNull(timeMillis = 50) { vm.effects.first() }
            assertNull(effect)
        }
}
