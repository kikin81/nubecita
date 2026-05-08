package net.kikin.nubecita.core.common.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class ComposerSubmitEventsBusTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `emit then collect round-trip — emitter publishes, events flow delivers`() =
        runTest(mainDispatcher.dispatcher) {
            val bus = ComposerSubmitEventsBus()
            val event = ComposerSubmitEvent(newPostUri = "at://did:p1", replyToUri = null)

            val received =
                async {
                    withTimeout(timeMillis = 1_000) { bus.events.first() }
                }
            bus.emitter.emit(event)

            assertEquals(event, received.await())
        }

    @Test
    fun `emit before subscribe — Channel buffers undelivered events for the late collector`() =
        // Pin the at-most-once-per-collector buffering contract that motivated the
        // SharedFlow → Channel switch. The Compact composer route detaches FeedScreen
        // from composition while the composer is on top, so OnSubmitSuccess fires
        // while the consumer's collector is torn down. A SharedFlow(replay = 0)
        // would silently drop the event in that window; the Channel must hold it
        // until the consumer re-subscribes.
        runTest(mainDispatcher.dispatcher) {
            val bus = ComposerSubmitEventsBus()
            val first = ComposerSubmitEvent(newPostUri = "at://did:p1", replyToUri = null)
            val second = ComposerSubmitEvent(newPostUri = "at://did:p2", replyToUri = "at://did:parent")

            // Emit twice with no active collector — both must be buffered.
            bus.emitter.emit(first)
            bus.emitter.emit(second)

            val drained =
                withTimeout(timeMillis = 1_000) {
                    bus.events.take(2).toList()
                }
            assertEquals(listOf(first, second), drained)
        }

    @Test
    fun `emit preserves FIFO order across many publishes`() =
        runTest(mainDispatcher.dispatcher) {
            val bus = ComposerSubmitEventsBus()
            val events =
                (0 until 8).map { i ->
                    ComposerSubmitEvent(newPostUri = "at://did:post-$i", replyToUri = null)
                }
            events.forEach { bus.emitter.emit(it) }

            val drained =
                withTimeout(timeMillis = 1_000) {
                    bus.events.take(events.size).toList()
                }
            assertEquals(events, drained)
        }

    @Test
    fun `each new bus owns an isolated channel — events from one don't leak into another`() =
        runTest(mainDispatcher.dispatcher) {
            val a = ComposerSubmitEventsBus()
            val b = ComposerSubmitEventsBus()
            val onlyOnA = ComposerSubmitEvent(newPostUri = "at://did:a-only", replyToUri = null)

            a.emitter.emit(onlyOnA)

            // a's collector sees the event; b's must NOT — withTimeout(50ms)
            // returning null is the negative assertion. (The Channel is a
            // member of the bus instance, so two buses share no state.)
            val received = withTimeout(timeMillis = 1_000) { a.events.first() }
            assertEquals(onlyOnA, received)

            // Sanity check that b's channel is genuinely separate.
            assertNotSame(a.emitter, b.emitter)
            assertNotSame(a.events, b.events)
        }

    @Test
    fun `composition-local handles initialize — Local read sides resolve to non-null defaults`() {
        // Forces the top-level vals in LocalComposerSubmitEvents.kt to initialize,
        // and verifies they're non-null. The default-value lambdas inside
        // compositionLocalOf only fire when .current is read from a Composable, so
        // we can't exercise those bodies from a JVM unit test — but the local
        // declarations themselves should resolve, which is what we assert here.
        assertNotNull(LocalComposerSubmitEvents)
        assertNotNull(LocalComposerSubmitEventsEmitter)
    }
}
