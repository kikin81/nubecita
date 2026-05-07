package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single-producer / multi-consumer event bus for composer submit
 * success notifications. Hosted by `MainShell` and exposed to
 * descendants via [LocalComposerSubmitEvents] so any screen reachable
 * inside the shell (today: the feed) can observe submits without the
 * composer having a direct compile-time dependency on its consumers.
 *
 * # Why not a callback parameter on the composer launcher?
 *
 * The composer is launched via [LocalComposerLauncher]'s
 * `(String?) -> Unit` lambda, which is intentionally minimal — adding
 * a per-call result callback would conflate "kick this off" with
 * "wait for this to finish" and would have to thread through every
 * launch site (FAB, reply icon, future deep links, etc.). A
 * shell-scoped event bus inverts that: launchers stay simple, and
 * any screen that wants to react opts in with a single `collect`.
 *
 * # Producer / consumer pattern
 *
 * - The composer hosts (`ComposerNavigationModule` for the Compact
 *   route and `ComposerOverlay` for the Medium / Expanded Dialog)
 *   call [emit] from `ComposerEffect.OnSubmitSuccess`'s screen-side
 *   handler.
 * - Consumers reach in via `LocalComposerSubmitEvents.current.events`
 *   and collect inside a `LaunchedEffect` keyed on the flow + their
 *   list state / snackbar host.
 *
 * # Buffer semantics
 *
 * `replay = 0`, `extraBufferCapacity = 1`,
 * `BufferOverflow.DROP_OLDEST` — same shape as
 * [LocalScrollToTopSignal]. Late subscribers don't see prior
 * emissions (a freshly-composed feed shouldn't show a "Reply sent"
 * for a submit that landed before it was visible). [emit] is
 * non-suspending via `tryEmit`; rapid back-to-back submits collapse
 * to the most recent event rather than queueing.
 *
 * # Default value
 *
 * The CompositionLocal default is a fresh, non-emitting instance —
 * previews / screenshot tests / detached compositions don't need to
 * wrap their content in a custom `CompositionLocalProvider`. Reading
 * `events` in those contexts returns a flow that never emits.
 */
@Stable
class ComposerSubmitEvents {
    private val _events =
        MutableSharedFlow<ComposerSubmitEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val events: SharedFlow<ComposerSubmitEvent> = _events.asSharedFlow()

    fun emit(event: ComposerSubmitEvent) {
        _events.tryEmit(event)
    }
}
