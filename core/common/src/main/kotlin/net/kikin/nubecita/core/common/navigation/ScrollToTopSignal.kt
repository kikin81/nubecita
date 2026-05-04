package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tap-to-top signal. Emitted by `MainShell` when the user re-taps the
 * currently-active bottom-nav tab. Feature screens that opt into the
 * gesture collect this flow inside a `LaunchedEffect` and call
 * `LazyListState.animateScrollToItem(0)`.
 *
 * # Contract
 *
 * The flow is a hot [SharedFlow] with `replay = 0` and a single-slot
 * buffer (`extraBufferCapacity = 1`,
 * `BufferOverflow.DROP_OLDEST`). The buffer choice is deliberate:
 *
 * - **No replay** — late subscribers don't see prior emissions. A tab
 *   that just became visible doesn't auto-scroll on entry.
 * - **Single-slot buffer + DROP_OLDEST** — [MutableSharedFlow.tryEmit]
 *   always succeeds. If the consumer's `collect { ... }` body is
 *   mid-suspend (e.g. running an `animateScrollToItem` from a prior
 *   emission, or restarting between recompositions), the new emission
 *   buffers and is delivered as soon as the body returns. Rapid
 *   double-taps collapse into a single scroll-to-top (DROP_OLDEST
 *   discards the buffered older one). Pure rendezvous semantics
 *   (`buffer = 0`) silently drop emissions during these windows, which
 *   manifests as "the tap registered but nothing happened."
 * - Producers should still call [MutableSharedFlow.tryEmit] (not
 *   `emit`) so the producer never suspends.
 *
 * Producers MUST emit ONLY on tab RE-TAP (`tappedTab == activeTab`),
 * never on a tab SWITCH. Firing on tab switch would defeat the
 * "tab restores last scroll position" mental model that Nav3's
 * per-tab back-stack already provides.
 *
 * Consumers see the read-only [SharedFlow] view (not [MutableSharedFlow])
 * — the CompositionLocal MUST NOT expose write capability beyond the
 * MainShell-side producer.
 *
 * # Default value
 *
 * The default is an empty silent [SharedFlow] that never emits, so
 * previews / screenshot tests / detached compositions don't need to
 * wrap the host in a custom [androidx.compose.runtime.CompositionLocalProvider].
 * Reading the default and collecting from it is a runtime no-op.
 *
 * # Why a CompositionLocal (not Hilt-injected)
 *
 * Per the project's MVI conventions (CLAUDE.md / `mvi-foundation`
 * spec), ViewModels MUST NOT access [androidx.compose.runtime.CompositionLocal]
 * values. The signal is consumed at the screen Composable layer (in a
 * `LaunchedEffect`), NOT in the VM. This keeps the VM pure and matches
 * the same pattern used by [LocalMainShellNavState] for tab-internal
 * navigation.
 *
 * # Consumer pattern
 *
 * ```kotlin
 * @Composable
 * fun MyScreen(...) {
 *     val listState = rememberLazyListState()
 *     val scrollToTopSignal = LocalScrollToTopSignal.current
 *     LaunchedEffect(scrollToTopSignal, listState) {
 *         scrollToTopSignal.collect { listState.animateScrollToItem(0) }
 *     }
 *     // ... rest of screen
 * }
 * ```
 *
 * # Producer pattern (`MainShell`)
 *
 * ```kotlin
 * val scrollToTopSignal = remember {
 *     MutableSharedFlow<Unit>(
 *         replay = 0,
 *         extraBufferCapacity = 1,
 *         onBufferOverflow = BufferOverflow.DROP_OLDEST,
 *     )
 * }
 * // Stable read-only view so the CompositionLocal value doesn't churn:
 * val readOnlyScrollToTopSignal = remember(scrollToTopSignal) {
 *     scrollToTopSignal.asSharedFlow()
 * }
 * // tab tap handler:
 * if (tapped == activeTab) scrollToTopSignal.tryEmit(Unit) else navigateToTab(tapped)
 *
 * CompositionLocalProvider(LocalScrollToTopSignal provides readOnlyScrollToTopSignal) {
 *     // ... NavDisplay etc.
 * }
 * ```
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`
 * so ktlint's `compose:compositionlocal-allowlist` rule lets it through,
 * matching the convention used by `LocalClock` and `LocalMainShellNavState`.
 */
val LocalScrollToTopSignal: ProvidableCompositionLocal<SharedFlow<Unit>> =
    compositionLocalOf { EmptyScrollToTopSignal }

/**
 * The default value of [LocalScrollToTopSignal] — a silent
 * [SharedFlow] that never emits. Held as a singleton so previews don't
 * pay an allocation per composition.
 */
private val EmptyScrollToTopSignal: SharedFlow<Unit> =
    MutableSharedFlow<Unit>(replay = 0).asSharedFlow()
