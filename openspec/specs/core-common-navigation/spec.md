# core-common-navigation Specification

## Purpose
TBD - created by archiving change add-oauth-flow. Update Purpose after archive.
## Requirements
### Requirement: `:core:common` provides a `Navigator` Hilt singleton owning the app back stack

`:core:common` SHALL expose a public `Navigator` interface and an internal `DefaultNavigator` implementation, bound `@Singleton` in Hilt's `SingletonComponent`. The interface SHALL expose:

- `val backStack: SnapshotStateList<NavKey>` — the live back stack, observable from Compose.
- `fun goTo(key: NavKey)` — appends `key` to the top of the stack.
- `fun goBack()` — removes the top of the stack (no-op if empty).
- `fun replaceTo(key: NavKey)` — clears the stack and appends `key`.

The default implementation SHALL initialize `backStack` containing the application's start destination (`Main`).

#### Scenario: ViewModel injects Navigator and pops the back stack

- **WHEN** a `@HiltViewModel` declares a constructor parameter of type `Navigator` and calls `navigator.goBack()` from a coroutine launched in `viewModelScope`
- **THEN** the top entry SHALL be removed from the back stack and `MainNavigation` SHALL render the new top destination on the next frame

#### Scenario: Empty stack `goBack()` is a no-op

- **WHEN** `navigator.goBack()` is called on an empty back stack
- **THEN** no exception SHALL be thrown and the stack SHALL remain empty

### Requirement: `MainNavigation` reads its back stack from the injected Navigator

`:app`'s `MainNavigation` composable SHALL obtain the navigation back stack from the `Navigator` Hilt singleton (via the existing `EntryPoint` pattern or constructor-injected wrapper) instead of calling `rememberNavBackStack(...)` locally. The `NavDisplay` `backStack` parameter SHALL receive `navigator.backStack` directly.

#### Scenario: Back-stack mutation from outside MainNavigation is observed

- **WHEN** any class with access to the `Navigator` (a ViewModel, a `LaunchedEffect`) calls `navigator.goTo(SomeNavKey)` while `MainNavigation` is composed
- **THEN** `NavDisplay` SHALL render the new destination on the next composition pass

#### Scenario: `MainNavigation` does not maintain a private back stack

- **WHEN** the `MainNavigation` source is inspected
- **THEN** it SHALL NOT call `rememberNavBackStack(...)`; the back stack passed to `NavDisplay` SHALL come from the Hilt-bound `Navigator`

### Requirement: `:core:common:navigation` provides `MainShellNavState` Compose-owned multi-tab state holder

`:core:common:navigation` SHALL expose a public `MainShellNavState` class and a `@Composable rememberMainShellNavState(startRoute: NavKey, topLevelRoutes: List<NavKey>): MainShellNavState` factory. `topLevelRoutes` SHALL be a `List` (not `Set`) because the factory issues one `rememberNavBackStack(key)` call per element in iteration order and Compose keys those `remember` slots by composer position; a reordered iteration would re-associate persisted stacks with the wrong keys. The factory SHALL `require` that `topLevelRoutes` contains unique elements and includes `startRoute`. The class SHALL hold:

- `topLevelKey: NavKey` — the active tab, mutable.
- A per-top-level-route map of back stacks (`NavBackStack<NavKey>` per route).
- A flattened `backStack: SnapshotStateList<NavKey>` view suitable for passing to `NavDisplay.backStack`, computed across the active tabs in "exit through home" order.

The class SHALL expose:

- `addTopLevel(key: NavKey)` — switch active tab; preserve the outgoing tab's stack.
- `add(key: NavKey)` — push `key` onto the active tab's stack.
- `removeLast()` — pop. If the popped key is a top-level route, the active tab SHALL switch back toward the start route per the recipe's "exit through home" rule.

The factory SHALL persist `topLevelKey` via `rememberSerializable(... NavKeySerializer ...)` and per-tab back stacks via `rememberNavBackStack(...)`, so configuration change and process death restore the prior state.

The class SHALL NOT be `@Inject`-able. It is intended to be created inside a Composable's body.

#### Scenario: Tab switch preserves outgoing stack

- **WHEN** `addTopLevel(Search)` is called from a state where the active tab is Feed and the Feed stack contains `[Feed, Profile("alice")]`
- **THEN** `topLevelKey` SHALL become `Search`, and a subsequent `addTopLevel(Feed)` SHALL restore Feed with the stack `[Feed, Profile("alice")]` intact

#### Scenario: Process-death round-trip restores state

- **WHEN** a `MainShellNavState` is created via `rememberMainShellNavState(...)`, mutated so the active tab is Feed with stack `[Feed, Profile("alice")]`, and the hosting Composable goes through a `saveInstanceState` → `recreate()` cycle
- **THEN** the post-recreation `MainShellNavState` SHALL report `topLevelKey == Feed` and the Feed back stack `[Feed, Profile("alice")]`

### Requirement: `:core:common:navigation` exposes `LocalMainShellNavState` `CompositionLocal`

`:core:common:navigation` SHALL expose `val LocalMainShellNavState: ProvidableCompositionLocal<MainShellNavState>` with no default value. `MainShell` SHALL provide it via `CompositionLocalProvider` so that descendant Composables can call `LocalMainShellNavState.current` to obtain the active state holder.

ViewModels SHALL NOT access `LocalMainShellNavState`. CompositionLocals are not reachable from a `ViewModel` — this constraint is enforced by the type system.

#### Scenario: Descendant Composable reads MainShellNavState from CompositionLocal

- **WHEN** a screen Composable inside the inner `NavDisplay` reads `LocalMainShellNavState.current` and calls `add(Profile(handle = "alice"))`
- **THEN** the active tab's back stack SHALL gain the `Profile(handle = "alice")` entry

#### Scenario: Reading LocalMainShellNavState outside MainShell throws

- **WHEN** a Composable not hosted inside `MainShell`'s `CompositionLocalProvider` reads `LocalMainShellNavState.current`
- **THEN** an `IllegalStateException` SHALL be thrown stating that no `MainShellNavState` is provided

### Requirement: `:core:common:navigation` provides `@OuterShell` and `@MainShell` Hilt qualifier annotations

`:core:common:navigation` SHALL expose two `@Qualifier`-annotated annotations:

- `@OuterShell` — for `EntryProviderInstaller` providers contributing to the outer `NavDisplay` (Splash, Login, the `Main` wrapper entry).
- `@MainShell` — for `EntryProviderInstaller` providers contributing to the inner `NavDisplay` hosted by `MainShell` (Feed, Search, Chats, You + their sub-routes).

Both qualifiers SHALL be retained at `BINARY` level. Feature modules contributing entries SHALL annotate their `@Provides @IntoSet` declarations with exactly one of these qualifiers. `:app`'s `NavigationEntryPoint` SHALL expose two distinct accessor methods, one annotated with each qualifier, returning `Set<@JvmSuppressWildcards EntryProviderInstaller>`.

#### Scenario: Outer-shell binding is collected via @OuterShell accessor

- **WHEN** a feature module declares `@Provides @IntoSet @OuterShell fun provide…(): EntryProviderInstaller = { entry<X> { … } }`
- **THEN** the binding SHALL be retrievable via `NavigationEntryPoint.outerEntryProviderInstallers()` and SHALL NOT appear in `NavigationEntryPoint.mainShellEntryProviderInstallers()`

#### Scenario: MainShell binding is collected via @MainShell accessor

- **WHEN** a feature module declares `@Provides @IntoSet @MainShell fun provide…(): EntryProviderInstaller = { entry<X> { … } }`
- **THEN** the binding SHALL be retrievable via `NavigationEntryPoint.mainShellEntryProviderInstallers()` and SHALL NOT appear in `NavigationEntryPoint.outerEntryProviderInstallers()`

#### Scenario: Unqualified binding is no longer collected

- **WHEN** a feature module declares `@Provides @IntoSet fun provide…(): EntryProviderInstaller = { entry<X> { … } }` without either qualifier
- **THEN** the binding SHALL NOT be collected by either accessor and the entry SHALL NOT be reachable through any `NavDisplay`

### Requirement: Existing feature modules migrate to qualified bindings

`:feature:login:impl`'s `EntryProviderInstaller` provider SHALL be annotated `@OuterShell`. `:feature:feed:impl`'s `EntryProviderInstaller` provider SHALL be annotated `@MainShell`. After this change, no `:feature:*:impl` module in the repository SHALL `@Provides @IntoSet` an `EntryProviderInstaller` without either `@OuterShell` or `@MainShell`.

#### Scenario: Repository scan finds no unqualified providers

- **WHEN** the repository is scanned for `@Provides @IntoSet fun .*: EntryProviderInstaller`
- **THEN** every match SHALL also carry `@OuterShell` or `@MainShell` on the same provider declaration

### Requirement: `LocalTabReTapSignal` exposes a feature-agnostic tab-re-tap broadcast

The system SHALL expose a `ProvidableCompositionLocal<SharedFlow<Unit>>` named `LocalTabReTapSignal` from `:core:common:navigation`. The contract:

- The flow MUST be a hot `SharedFlow<Unit>` with `replay = 0` and a single-slot drop-oldest buffer (`extraBufferCapacity = 1`, `BufferOverflow.DROP_OLDEST`). The buffer guarantees `tryEmit` always succeeds even when the consumer's `collect { ... }` body is mid-suspend (e.g. running an animation from a prior emission, or briefly restarting between recompositions). Rapid double-taps from the producer collapse into a single delivered emission (DROP_OLDEST discards the older buffered one).
- The default value MUST be an empty `SharedFlow<Unit>` (a `MutableSharedFlow<Unit>(replay = 0).asSharedFlow()`) so previews / screenshot tests / detached compositions don't need to wrap the host in a custom `CompositionLocalProvider`. Reading the default and collecting from it MUST be a runtime no-op.
- The producer (typically `MainShell`) is the sole writer; consumers are read-only via the `SharedFlow<Unit>` shape (not `MutableSharedFlow`). The CompositionLocal MUST NOT expose write capability to consumers. The producer SHOULD `remember` the `asSharedFlow()` wrapper so the CompositionLocal value stays stable across recompositions (otherwise consumers' `LaunchedEffect`s keyed on the flow restart unnecessarily).
- Consumers (feature screens) collect the flow inside a `LaunchedEffect` keyed on `(signal, listState)` (or equivalent stable keys) and perform their re-tap action — typically `LazyListState.animateScrollToItem(0)`, though sibling tabs MAY bind non-scroll actions to the same signal. The signal carries no payload — it's a pure trigger.

#### Scenario: Producer emits, single consumer scrolls

- **WHEN** a `LocalTabReTapSignal` provider emits `Unit` while a feature screen has an active `LaunchedEffect` collector
- **THEN** the collector receives the emission within one frame and calls `animateScrollToItem(0)` on the bound `LazyListState`.

#### Scenario: Emission with no awaiting subscriber buffers and delivers when collection resumes

- **WHEN** the producer calls `tryEmit(Unit)` and the consumer's `collect { ... }` body is currently mid-suspend (or briefly restarting between recompositions)
- **THEN** `tryEmit` returns `true` (the single-slot buffer accepts the emission) and the emission is delivered as soon as the consumer's body returns to its awaiting state.

#### Scenario: Rapid double-emit collapses into a single delivered emission

- **WHEN** the producer calls `tryEmit(Unit)` twice within a window where the consumer's body is mid-suspend
- **THEN** the buffer's DROP_OLDEST policy keeps only the most recent emission. The consumer's body runs once with `Unit` after returning to the awaiting state; the older buffered emission is discarded. (The user perceives a single scroll-to-top, not two queued.)

#### Scenario: Default value supports preview composition

- **WHEN** a feature screen renders inside a preview / screenshot test that does NOT wrap composition in a `LocalTabReTapSignal` provider
- **THEN** `LocalTabReTapSignal.current` returns the default empty `SharedFlow<Unit>`. The screen's `LaunchedEffect` collector subscribes successfully but never receives an emission. No exception is thrown.

#### Scenario: Multiple subscribers all receive the broadcast

- **WHEN** two feature screens are simultaneously composed (e.g., adaptive split-pane on a tablet) and both collect `LocalTabReTapSignal`
- **THEN** a single `tryEmit(Unit)` from the producer SHALL deliver to BOTH collectors. Each screen scrolls its own `LazyListState` to position 0 in parallel.

### Requirement: MainShell emits the signal on bottom-nav tab RE-TAP only

The `:app/MainShell` composable SHALL provide a `LocalTabReTapSignal` value via `CompositionLocalProvider` and emit `Unit` from its bottom-nav tab-tap handler when and only when the tapped tab equals the currently-active tab.

- A tap that switches tabs (`tappedTab != activeTab`) MUST navigate as before and MUST NOT emit the signal. The destination tab restores its last scroll position via Nav3's existing back-stack semantics; firing scroll-to-top on a fresh tab landing would defeat that.
- A tap that re-selects the active tab (`tappedTab == activeTab`) MUST call `tryEmit(Unit)` on the underlying `MutableSharedFlow` and MUST NOT navigate. The user remains on the same tab; the signal is the only side effect.
- The tab-tap handler MUST resolve `activeTab` from the post-mutation MainShell state (not the pre-tap snapshot) so a rapid double-tap during a tab-switch animation is interpreted correctly.

#### Scenario: Re-tap on the active tab fires the signal

- **WHEN** the user taps the bottom-nav `Feed` tab while `activeTab == Feed`
- **THEN** MainShell calls `tryEmit(Unit)` on the underlying `MutableSharedFlow`. No navigation occurs. Any feature screen collecting `LocalTabReTapSignal` receives the emission.

#### Scenario: Tab switch does not fire the signal

- **WHEN** the user taps the bottom-nav `Profile` tab while `activeTab == Feed`
- **THEN** MainShell navigates to `Profile`. NO signal is emitted. Profile's screen restores its last scroll position untouched.
