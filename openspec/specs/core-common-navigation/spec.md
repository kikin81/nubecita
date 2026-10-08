# core-common-navigation Specification

## Purpose
The shared navigation primitives in `:core:common`: the injectable outer `Navigator` that owns the app back stack, the Compose-owned `MainShellNavState` multi-tab state holder and its `LocalMainShellNavState` `CompositionLocal`, the `@OuterShell` / `@MainShell` qualifiers that route each feature's `EntryProviderInstaller` to the right `NavDisplay`, and the `LocalTabReTapSignal` re-tap broadcast.
## Requirements
### Requirement: `:core:common` provides a `Navigator` Hilt singleton owning the app back stack

`:core:common` SHALL expose a public `Navigator` interface and internal `DefaultNavigator` bound `@Singleton` in `SingletonComponent`. The interface SHALL expose `val backStack: SnapshotStateList<NavKey>`, `fun goTo(key: NavKey)`, `fun goBack()`, and `fun replaceTo(key: NavKey)`. `backStack` SHALL initialize containing start destination `Main`.

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

`:core:common:navigation` SHALL expose `MainShellNavState` and `@Composable rememberMainShellNavState(startRoute: NavKey, topLevelRoutes: List<NavKey>): MainShellNavState`. It holds `topLevelKey: NavKey`, a per-route map of back stacks, and flattened `backStack: SnapshotStateList<NavKey>`. It exposes `addTopLevel(key)`, `add(key)`, and `removeLast()`. State is persisted via `rememberSerializable` and `rememberNavBackStack`. The class is not `@Inject`-able.

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

`:core:common:navigation` SHALL expose `@Qualifier` annotations `@OuterShell` (for outer `NavDisplay`) and `@MainShell` (for inner `NavDisplay` hosted by `MainShell`), retained at `BINARY` level. Feature module `@Provides @IntoSet EntryProviderInstaller` declarations SHALL use exactly one qualifier. `:app`'s `NavigationEntryPoint` SHALL expose separate accessors for each qualifier.

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

`:core:common:navigation` SHALL expose `LocalTabReTapSignal: ProvidableCompositionLocal<SharedFlow<Unit>>`. The hot flow MUST have `replay = 0`, `extraBufferCapacity = 1`, and `BufferOverflow.DROP_OLDEST`. The default value MUST be an empty `SharedFlow<Unit>`. `MainShell` is sole writer via `MutableSharedFlow`; consumers read via `SharedFlow<Unit>`. Feature screens collect the flow in `LaunchedEffect` to trigger re-tap actions (e.g., scroll to top).

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

`:app/MainShell` SHALL provide `LocalTabReTapSignal` and call `tryEmit(Unit)` when and only when the tapped bottom-nav tab equals `activeTab`. Switching tabs (`tappedTab != activeTab`) MUST navigate without emitting the signal. Re-tapping active tab MUST call `tryEmit(Unit)` and MUST NOT navigate. `activeTab` MUST resolve from post-mutation state.

#### Scenario: Re-tap on the active tab fires the signal

- **WHEN** the user taps the bottom-nav `Feed` tab while `activeTab == Feed`
- **THEN** MainShell calls `tryEmit(Unit)` on the underlying `MutableSharedFlow`. No navigation occurs. Any feature screen collecting `LocalTabReTapSignal` receives the emission.

#### Scenario: Tab switch does not fire the signal

- **WHEN** the user taps the bottom-nav `Profile` tab while `activeTab == Feed`
- **THEN** MainShell navigates to `Profile`. NO signal is emitted. Profile's screen restores its last scroll position untouched.
