package net.kikin.nubecita.wear.feed

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TickWhileStartedTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun ticksOnceAMinuteWhileStarted() =
        runTest(main.dispatcher) {
            val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
            val ticks = mutableListOf<Long>()
            val job = launch { owner.lifecycle.tickWhileStarted(clock = { currentTime }) { ticks += it } }
            runCurrent()
            advanceTimeBy(MINUTE_MS * 2)
            runCurrent()

            assertEquals(listOf(0L, MINUTE_MS, MINUTE_MS * 2), ticks)
            job.cancel()
        }

    @Test
    fun stopsTickingWhileStopped_andRefreshesOnStart() =
        runTest(main.dispatcher) {
            val owner = Owner().apply { registry.currentState = Lifecycle.State.STARTED }
            val ticks = mutableListOf<Long>()
            val job = launch { owner.lifecycle.tickWhileStarted(clock = { currentTime }) { ticks += it } }
            runCurrent()

            // Backgrounded: an hour passes with no ticks (battery).
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            advanceTimeBy(MINUTE_MS * 60)
            runCurrent()
            assertEquals(listOf(0L), ticks)

            // Back in the foreground: the time refreshes at once, not up to a minute later.
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf(0L, MINUTE_MS * 60), ticks)
            job.cancel()
        }
}
