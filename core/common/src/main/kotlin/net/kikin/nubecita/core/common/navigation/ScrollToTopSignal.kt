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
 * The flow is a hot [SharedFlow] with `replay = 0` and no extra buffer
 * (`extraBufferCapacity = 0`) — emissions while no subscriber is
 * collecting are dropped silently. That's the right semantic: a tab
 * that isn't the visible one has no list to scroll, so its missing
 * subscriber is a feature, not a bug. Producers MUST use
 * [MutableSharedFlow.tryEmit] (which returns `false` and no-ops on
 * "no subscriber" rather than suspending or buffering).
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
 *     MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 0)
 * }
 * // tab tap handler:
 * if (tapped == activeTab) scrollToTopSignal.tryEmit(Unit) else navigateToTab(tapped)
 *
 * CompositionLocalProvider(LocalScrollToTopSignal provides scrollToTopSignal.asSharedFlow()) {
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
