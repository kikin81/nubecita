package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Multi-producer / single-consumer event bus for composer submit
 * success notifications. The producer side has two host sites that
 * publish independently — `ComposerOverlay` (Medium / Expanded Dialog)
 * and `ComposerNavigationModule`'s `ComposerRoute` entry (Compact
 * NavDisplay route). The consumer side is single (today: only
 * `FeedScreen` collects).
 *
 * Hosted by `MainShell` and exposed to descendants via two separate
 * CompositionLocals so any screen reachable inside the shell can
 * observe submits without the composer having a direct compile-time
 * dependency on its consumers:
 *
 * - [LocalComposerSubmitEvents] — consumer-facing read-only [Flow].
 *   Reading this local can't accidentally emit because [Flow] has no
 *   write surface.
 * - [LocalComposerSubmitEventsEmitter] — producer-facing
 *   [ComposerSubmitEventsEmitter] (a one-method `fun interface`).
 *
 * # API separation, not access control
 *
 * Both CompositionLocals are provided at the `MainShell` root, so any
 * descendant of `MainShell` could in principle read
 * [LocalComposerSubmitEventsEmitter] and call
 * [ComposerSubmitEventsEmitter.emit]. The split is *intent-revealing
 * typing*: the consumer-facing local exposes a [Flow] (no `.emit` on
 * it), so the common-case "I want to react to submits" call site can't
 * accidentally publish; and the producer/consumer boundary is visible
 * at the call site by which local you reach for. The convention is
 * that only the two composer hosts emit.
 *
 * Mirrors the producer/consumer split of [LocalScrollToTopSignal] —
 * in that case `MainShell` is the sole producer and holds the
 * [kotlinx.coroutines.flow.MutableSharedFlow] locally, but here the
 * producers live in different modules from `MainShell`, so the emit
 * side has to travel through a CompositionLocal too. If we ever need
 * true access control, the path forward is a Hilt-singleton bus
 * injected at the host sites instead of a CompositionLocal — scoped
 * out of this change.
 *
 * # Why a Channel and not a SharedFlow?
 *
 * The Compact composer hosting model pushes `ComposerRoute` onto the
 * inner `NavDisplay`, which removes the underlying screen (e.g.
 * `FeedScreen`) from composition while the composer is on top. That
 * cancellation tears down the consumer's `LaunchedEffect` collector.
 * `ComposerEffect.OnSubmitSuccess` fires AFTER the user submits but
 * BEFORE `navState.removeLast()` resolves into a recomposition that
 * brings the consumer back — so at the moment of emit there is no
 * active collector. A `MutableSharedFlow(replay = 0, ...)` would
 * silently drop the event in that window. A [Channel]-backed flow
 * **buffers undelivered events** until the next collector subscribes
 * and drains the queue, which is the delivery semantics we actually
 * want here ("you must hear about this submit" is a stronger contract
 * than "you might hear about it if you happen to be listening").
 *
 * Trade-off: at-most-once-per-collector semantics. If a future surface
 * (e.g., a separate analytics observer) wants to watch the same
 * stream, the events are split, not multiplexed. We're explicitly
 * single-consumer for now — only `FeedScreen` reads from this bus —
 * so this is fine. If a second consumer ever needs it, fan out via a
 * `MutableSharedFlow(replay = 1)` with explicit consume-and-tombstone
 * at that point.
 *
 * # Producer / consumer pattern
 *
 * - The composer hosts (`ComposerNavigationModule` for the Compact
 *   route and `ComposerOverlay` for the Medium / Expanded Dialog)
 *   reach [LocalComposerSubmitEventsEmitter] and call
 *   [ComposerSubmitEventsEmitter.emit] from
 *   `ComposerEffect.OnSubmitSuccess`'s screen-side handler.
 * - Consumers reach [LocalComposerSubmitEvents] and collect the
 *   [Flow] inside a `LaunchedEffect` keyed on the flow + their list
 *   state / snackbar host.
 *
 * # Buffer semantics
 *
 * `Channel(capacity = Channel.BUFFERED)` — defaults to a 64-slot
 * buffer; rapid back-to-back submits queue rather than dropping. In
 * practice we'd never expect more than 1 unconsumed submit event,
 * so the upper bound is irrelevant; what matters is that nothing is
 * lost while the consumer is detached. Emit is non-suspending via
 * [Channel.trySend].
 */
@Stable
class ComposerSubmitEventsBus {
    private val channel = Channel<ComposerSubmitEvent>(capacity = Channel.BUFFERED)

    /**
     * Read-only stream surfaced to consumers via [LocalComposerSubmitEvents].
     * See class kdoc for the at-most-once-per-collector trade-off.
     */
    val events: Flow<ComposerSubmitEvent> = channel.receiveAsFlow()

    /**
     * Write-only handle surfaced to composer hosts via
     * [LocalComposerSubmitEventsEmitter]. Calls
     * [Channel.trySend] under the hood; non-suspending.
     */
    val emitter: ComposerSubmitEventsEmitter =
        ComposerSubmitEventsEmitter { event -> channel.trySend(event) }
}

/**
 * Producer-facing handle for [ComposerSubmitEventsBus]. Held by the
 * composer hosts via [LocalComposerSubmitEventsEmitter]; not exposed
 * to consumers (which see [LocalComposerSubmitEvents]'s read-only
 * [Flow] instead).
 */
fun interface ComposerSubmitEventsEmitter {
    fun emit(event: ComposerSubmitEvent)
}
