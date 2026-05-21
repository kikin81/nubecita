package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bottom-nav tab RE-TAP signal. Emitted by `MainShell` whenever the
 * user taps the bottom-nav item for the currently-active tab. Each
 * top-level destination interprets the re-tap differently:
 *
 *  - **Feed** — scroll the feed list back to position 0
 *    (`LazyListState.animateScrollToItem(0)`).
 *  - **Search** — focus the search `TextField` + open the soft
 *    keyboard. Matches the Play Store / Twitter / Bluesky convention
 *    for re-tapping a search tab.
 *  - **Profile** — scroll the profile feed list back to position 0
 *    (same shape as Feed).
 *  - **Chats** — not wired today; the bus emits regardless so adding a
 *    consumer is a Composable-only change.
 *
 * # Why one signal, no payload
 *
 * The signal is `Unit` rather than carrying the re-tapped [androidx.navigation3.runtime.NavKey].
 * Nav3 with per-tab back-stacks only composes the active tab's
 * content, so any subscriber sees emissions only while ITS tab is on
 * screen — the active-tab identity is implicit. Adding a `NavKey`
 * payload would push a filter (`.filter { it == myTabKey }`) into
 * every consumer for zero practical benefit today. Revisit only if
 * the architecture ever composes inactive tabs (preview surfaces,
 * pre-warming).
 *
 * # Contract
 *
 * The flow is a hot [SharedFlow] with `replay = 0` and a single-slot
 * buffer (`extraBufferCapacity = 1`,
 * `BufferOverflow.DROP_OLDEST`). The buffer choice is deliberate:
 *
 * - **No replay** — late subscribers don't see prior emissions. A tab
 *   that just became visible doesn't auto-fire its consumer on entry.
 * - **Single-slot buffer + DROP_OLDEST** — [MutableSharedFlow.tryEmit]
 *   always succeeds. If the consumer's `collect { ... }` body is
 *   mid-suspend (e.g. running an `animateScrollToItem` from a prior
 *   emission, or restarting between recompositions), the new emission
 *   buffers and is delivered as soon as the body returns. Rapid
 *   double-taps collapse into a single action (DROP_OLDEST discards
 *   the buffered older one). Pure rendezvous semantics (`buffer = 0`)
 *   silently drop emissions during these windows, which manifests as
 *   "the tap registered but nothing happened."
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
 * # Consumer pattern (scroll-to-top — Feed / Profile)
 *
 * ```kotlin
 * @Composable
 * fun FeedScreen(...) {
 *     val listState = rememberLazyListState()
 *     val tabReTapSignal = LocalTabReTapSignal.current
 *     LaunchedEffect(tabReTapSignal, listState) {
 *         tabReTapSignal.collect { listState.animateScrollToItem(0) }
 *     }
 *     // ... rest of screen
 * }
 * ```
 *
 * # Consumer pattern (focus + IME — Search)
 *
 * ```kotlin
 * val focusRequester = remember { FocusRequester() }
 * val keyboard = LocalSoftwareKeyboardController.current
 * val tabReTapSignal = LocalTabReTapSignal.current
 * LaunchedEffect(tabReTapSignal, focusRequester, keyboard) {
 *     tabReTapSignal.collect {
 *         focusRequester.requestFocus()
 *         keyboard?.show()
 *     }
 * }
 * // Apply `Modifier.focusRequester(focusRequester)` to the TextField.
 * ```
 *
 * # Producer pattern (`MainShell`)
 *
 * ```kotlin
 * val tabReTapSignal = remember {
 *     MutableSharedFlow<Unit>(
 *         replay = 0,
 *         extraBufferCapacity = 1,
 *         onBufferOverflow = BufferOverflow.DROP_OLDEST,
 *     )
 * }
 * // Stable read-only view so the CompositionLocal value doesn't churn:
 * val readOnlyTabReTapSignal = remember(tabReTapSignal) {
 *     tabReTapSignal.asSharedFlow()
 * }
 * // tab tap handler:
 * if (tapped == activeTab) tabReTapSignal.tryEmit(Unit) else navigateToTab(tapped)
 *
 * CompositionLocalProvider(LocalTabReTapSignal provides readOnlyTabReTapSignal) {
 *     // ... NavDisplay etc.
 * }
 * ```
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`
 * so ktlint's `compose:compositionlocal-allowlist` rule lets it through,
 * matching the convention used by `LocalClock` and `LocalMainShellNavState`.
 */
@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalTabReTapSignal: ProvidableCompositionLocal<SharedFlow<Unit>> =
    compositionLocalOf { EmptyTabReTapSignal }

/**
 * The default value of [LocalTabReTapSignal] — a silent
 * [SharedFlow] that never emits. Held as a singleton so previews don't
 * pay an allocation per composition.
 */
private val EmptyTabReTapSignal: SharedFlow<Unit> =
    MutableSharedFlow<Unit>(replay = 0).asSharedFlow()
